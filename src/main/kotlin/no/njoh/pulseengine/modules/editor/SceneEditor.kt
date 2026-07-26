package no.njoh.pulseengine.modules.editor

import gnu.trove.map.hash.THashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.input.CursorType.*
import no.njoh.pulseengine.core.console.CommandResult
import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.*
import no.njoh.pulseengine.core.graphics.postprocessing.FrostedGlassEffect
import no.njoh.pulseengine.core.input.CursorMode
import no.njoh.pulseengine.modules.ui.UiUtils.findElement
import no.njoh.pulseengine.modules.ui.UiUtils.firstElementOrNull
import no.njoh.pulseengine.modules.ui.elements.InputField
import no.njoh.pulseengine.modules.ui.UiElement
import no.njoh.pulseengine.modules.ui.layout.RowPanel
import no.njoh.pulseengine.modules.ui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.ui.layout.VerticalPanel
import no.njoh.pulseengine.modules.ui.layout.docking.DockingPanel
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.input.Key.*
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.Scene
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.EDITABLE
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SELECTED
import no.njoh.pulseengine.core.scene.SceneEntityFilter.Entities
import no.njoh.pulseengine.core.scene.interfaces.Spatial2D
import no.njoh.pulseengine.modules.physics2d.PhysicsEntity2D
import no.njoh.pulseengine.modules.physics2d.bodies.PhysicsBody2D
import no.njoh.pulseengine.core.shared.utils.FileChooser
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.isNotIn
import no.njoh.pulseengine.core.service.Service
import no.njoh.pulseengine.modules.ui.UiParams.UI_SCALE
import no.njoh.pulseengine.modules.ui.elements.Button
import no.njoh.pulseengine.modules.ui.layout.Panel
import no.njoh.pulseengine.modules.ui.layout.WindowPanel
import no.njoh.pulseengine.modules.scene.systems.EntityRendererImpl
import no.njoh.pulseengine.modules.scene.systems.EntityUpdater
import no.njoh.pulseengine.modules.editor.EditorUtil.getName
import no.njoh.pulseengine.modules.editor.EditorUtil.getPropGroup
import no.njoh.pulseengine.modules.editor.EditorUtil.getPropInfo
import no.njoh.pulseengine.modules.editor.EditorUtil.isEditable
import no.njoh.pulseengine.modules.editor.EditorUtil.setPrimitiveProperty
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.*

@Suppress("FunctionName")
fun SceneEditor2D(vararg initialScenes: String) = SceneEditor(ViewportInteraction2D(), initialScenes = initialScenes.toList())

@Suppress("FunctionName")
fun SceneEditor3D(vararg initialScenes: String) = SceneEditor(ViewportInteraction3D(), initialScenes = initialScenes.toList())

class SceneEditor(
    val viewportInteraction: ViewportInteraction? = ViewportInteraction2D(),
    val uiFactory: UiElementFactory = UiElementFactory(),
    initialScenes: List<String> = emptyList()
): Service() {

    // UI
    lateinit var viewportArea: FocusArea
    lateinit var rootUI: VerticalPanel
    lateinit var inspectorUI: RowPanel
    lateinit var systemPropertiesUI: RowPanel
    lateinit var dockingUI: DockingPanel
    lateinit var viewportContext: ViewportContext
    lateinit var sceneTabsUI: HorizontalPanel

    private var entityPropertyUiRows = THashMap<String, UiElement>()
    private var collapsedPropertyHeaders = mutableListOf<String>()
    private var updateFooterCallback: (totalEntities: Int, selectedEntities: Int, sceneName: String) -> Unit = { _,_,_ -> }
    private var showGrid = true
    private var outliner: Outliner? = null

    // Camera
    private lateinit var activeCamera: Camera

    // Scene
    private var lastSceneHashCode = -1
    private val entitySelection = mutableListOf<SceneEntity>()
    private val sceneFilesToLoad = ConcurrentLinkedQueue<String>()
    private var sceneFileToCreate: String? = null
    private var sceneFileToSaveAs: String? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val editorScenes = mutableListOf<EditorScene>()
    private val initialSceneFiles = initialScenes.toList()

    // Copying
    private var isCopying = false
    private var selectedEntityIdsBeforePlay = emptyList<Long>()

    // Loading and saving
    private var shouldPersistEditorLayout = false
    private var lastSaveLoadDirectory = ""

    override fun onCreate(engine: PulseEngine)
    {
        uiFactory.entityProvider = { engine.scene.getAllEntitiesByType().flatMap { it } }

        // Load editor config
        engine.config.load("/pulseengine/config/editor_default.cfg")
        if (engine.config.getBool("openEditorOnStart") == true)
            start()

        UI_SCALE = engine.window.contentScale

        // Set editor data
        viewportArea = FocusArea(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
        activeCamera = engine.gfx.mainCamera
        shouldPersistEditorLayout = engine.config.getBool("persistEditorLayout") ?: false
        lastSaveLoadDirectory = engine.config.saveDirectory
        loadInitialEditorScenes(engine)

        // Create surfaces
        engine.gfx.createSurface("scene_editor_ui_base_bg",  zOrder = -90)
        engine.gfx.createSurface("scene_editor_ui_base",     zOrder = -92, multisampling = MSAA16)
        engine.gfx.createSurface("scene_editor_ui_popup_bg", zOrder = -93)
        engine.gfx.createSurface("scene_editor_ui_popup",    zOrder = -94, multisampling = MSAA16)

        // Load editor icon font
        engine.asset.load(Font("/pulseengine/assets/editor_icons.ttf", uiFactory.style.iconFontName))

        // Register a console command to toggle editor visibility
        engine.console.registerCommand("showSceneEditor")
        {
            if (isRunning) stopEditorAndStartGame(engine) else stopGameAndStartEditor(engine)
            CommandResult("", showCommand = false)
        }

        // Editor keyboard shortcuts
        engine.input.setOnKeyPressed()
        {
            if (isRunning && it == DELETE && engine.input.hasFocus(viewportArea))
                deleteSelectedEntities(engine)
            
            if (isRunning && it == S && engine.input.isPressed(LEFT_CONTROL))
                saveActiveEditorScene(engine)
            
            if (isRunning && isControlPressed(engine) && !hasFocusedInputField(engine))
            {
                when (it)
                {
                    C -> copySelectedEntitiesToClipboard(engine)
                    V -> pasteEntitiesFromClipboard(engine)
                    else -> {}
                }
            }
        }

        // React on scale changes
        engine.window.setOnContentScaleChanged()
        {
            createSceneEditorUI(engine)
        }

        // Create and populate editor with UI
        createSceneEditorUI(engine)

        viewportContext = ViewportContext(this, activeCamera, viewportArea)
        viewportInteraction?.onCreate(engine, viewportContext)
        if (isRunning)
            viewportInteraction?.onEditorActivated(engine, viewportContext)
    }

    private fun createSceneEditorUI(engine: PulseEngine)
    {
        // Set UI scaling
        UI_SCALE = engine.window.contentScale

        // Properties
        inspectorUI = RowPanel()
        systemPropertiesUI = RowPanel()

        // Create content
        val menuBar = uiFactory.createMenuBarUI(
            MenuBarButton("File", listOf(
                MenuBarItem("New...")     { onNewScene(engine) },
                MenuBarItem("Open...")    { onLoad(engine) },
                MenuBarItem("Save")       { saveActiveEditorScene(engine) },
                MenuBarItem("Save as...") { onSaveAs(engine) },
                MenuBarItem("Close")      { closeActiveEditorScene(engine) }
            )),
            MenuBarButton("View", listOf(
                MenuBarItem("Entity Inspector") { createInspectorWindow() },
                MenuBarItem("Scene Hierarchy")  { createOutlinerWindow(engine) },
                MenuBarItem("Scene systems") { createSceneSystemsPropertyWindow(engine) },
                MenuBarItem("Viewport")      { createViewportWindow(engine) },
                MenuBarItem("Grid")          { showGrid = !showGrid },
                MenuBarItem("Reset")
                {
                    createSceneEditorUI(engine)
                    showGrid = true
                    viewportInteraction?.resetCamera(engine, viewportContext)
                }
            )),
            MenuBarButton("Run", listOf(
                MenuBarItem("Start") { stopEditorAndStartGame(engine) },
                MenuBarItem("Stop")  { stopGameAndStartEditor(engine) },
            ))
        )

        // Panel for docking of windows
        dockingUI = DockingPanel()
        dockingUI.focusable = false

        // Footer
        val (footer, callback) = uiFactory.createFooter()
        updateFooterCallback = callback

        // Create root UI and perform initial update
        rootUI = VerticalPanel()
        rootUI.focusable = false
        sceneTabsUI = uiFactory.createSceneTabsUI(engine, createSceneTabModels(engine))
        rootUI.addChildren(menuBar, sceneTabsUI, dockingUI, footer)
        rootUI.updateLayout()
        rootUI.setLayoutClean()

        // Create default windows and insert into docking
        createSceneSystemsPropertyWindow(engine)
        createOutlinerWindow(engine)
        createInspectorWindow()

        // Load previous layout from file
        if (shouldPersistEditorLayout)
            dockingUI.loadLayout(engine, "/editor_layout.cfg")
    }

    private fun createSceneSystemsPropertyWindow(engine: PulseEngine)
    {
        if (dockingUI.findElement("Scene Systems") != null)
            return // Already exists

        updateSceneSystemProperties(engine)
        val sceneSystemPropertiesUi = uiFactory.createSystemPropertiesPanelUI(
            engine = engine,
            propertiesRowPanel = systemPropertiesUI,
            onChanged = { markActiveEditorSceneDirty(engine) }
        )
        val sceneSystemWindow = uiFactory.createWindowUI("Scene Systems", "GEARS")
        sceneSystemWindow.body.addChildren(sceneSystemPropertiesUi)
        dockingUI.insertRight(sceneSystemWindow)
    }

    private fun createOutlinerWindow(engine: PulseEngine)
    {
        if (dockingUI.findElement("Outliner") != null)
            return // Already exists

        outliner = Outliner.build(
            engine = engine,
            uiElementFactory = uiFactory,
            onEntitiesSelected = {
                entitySelection.clear()
                inspectorUI.clearChildren()
                entityPropertyUiRows.clear()
                engine.scene.forEachEntity()
                {
                    if (it.isSet(SELECTED or EDITABLE) && it.isNot(HIDDEN))
                        addEntityToSelection(it)
                }
                if (entitySelection.size == 1)
                    selectSingleEntity(engine, entitySelection.first())
            },
            onEntityCreated = { type -> createNewEntity(engine, type) },
            onEntityDeleted = { deleteSelectedEntities(engine) }
        )
        outliner!!.reloadEntitiesFromActiveScene()

        val window = uiFactory.createWindowUI(title = "Scene Hierarchy", iconName = "LIST", onClosed = { outliner = null })
        window.id = "Outliner" // Keep the stable ID used by saved editor layouts
        window.body.addChildren(outliner!!.ui)
        dockingUI.insertLeft(window)
    }

    private fun createInspectorWindow()
    {
        if (dockingUI.findElement("Inspector") != null)
            return // Already exists

        val inspectorWindow = uiFactory.createWindowUI("Entity Inspector", "CUBE")
        inspectorWindow.id = "Inspector" // Keep the stable ID used by saved editor layouts
        val propertyPanel = uiFactory.createScrollableSectionUI(inspectorUI)
        inspectorWindow.body.addChildren(propertyPanel)

        val propWindow = dockingUI.findElement("Outliner")
        if (propWindow != null && propWindow.parent != dockingUI) // If parent is docking then it is a free floating window
            dockingUI.insertInsideBottom(target = propWindow as WindowPanel, inspectorWindow)
        else
            dockingUI.insertLeft(inspectorWindow)
    }

    private fun createViewportWindow(engine: PulseEngine)
    {
        val viewportUi = uiFactory.createViewportUI(engine)
        val viewportWindow = uiFactory.createWindowUI("Viewport", "MONITOR",300f, 300f, 640f, 480f)
        viewportWindow.body.addChildren(viewportUi)
        dockingUI.addChildren(viewportWindow)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        while (true)
        {
            val sceneFile = sceneFilesToLoad.poll() ?: break
            openEditorScene(engine, sceneFile)
        }

        sceneFileToCreate?.let()
        {
            val sceneName = it.substringAfterLast("/").substringAfterLast("\\").substringBefore(".")
            val editorScene = EditorScene(Scene(sceneName).also { scene -> scene.fileName = it })
            editorScenes.add(editorScene)
            switchToEditorScene(engine, editorScene)
            engine.scene.addSystem(EntityUpdater().also { it.init(engine) })
            engine.scene.addSystem(EntityRendererImpl().also { it.init(engine) })
            saveActiveEditorScene(engine)
            sceneFileToCreate = null
        }

        sceneFileToSaveAs?.let()
        {
            engine.scene.saveAs(fileName = it, updateActiveScene = true)
            activeEditorScene(engine)?.dirty = false
            rebuildSceneTabs(engine)
            sceneFileToSaveAs = null
        }

        ensureActiveEditorScene(engine)

        if (engine.scene.state == SceneState.STOPPED)
        {
            viewportArea.update(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
            engine.input.requestFocus(viewportArea)
        }

        if (engine.scene.activeScene.hashCode() != lastSceneHashCode)
        {
            resetUI(engine)
            updateSceneSystemProperties(engine)
            initializeEntities(engine)
            outliner?.reloadEntitiesFromActiveScene()
            lastSceneHashCode = engine.scene.activeScene.hashCode()
        }

        if (engine.scene.state == SceneState.STOPPED)
        {
            viewportInteraction?.onUpdate(engine, viewportContext)
            handleEntityCopying(engine)
        }

        if (engine.input.wasClicked(F10))
        {
            if (engine.scene.state == SceneState.STOPPED)
            {
                rememberEntitySelection()
                engine.scene.save()
                engine.scene.start()
            }
            else
            {
                engine.scene.stop()
                engine.scene.reload()
                ensureActiveEditorScene(engine, replaceSceneWithSameFile = true)
            }
        }

        updateFooterCallback(
            engine.scene.getAllEntitiesByType().sumOf { it.size },
            entitySelection.size,
            engine.scene.activeScene.fileName
        )

        if (rootUI.width.value.toInt() != engine.window.width || rootUI.height.value.toInt() != engine.window.height)
            rootUI.setLayoutDirty()

        rootUI.update(engine)
    }

    override fun onRender(engine: PulseEngine)
    {
        val uiBaseSurface    = engine.gfx.getSurfaceOrDefault("scene_editor_ui_base")
        val uiPopupSurface   = engine.gfx.getSurfaceOrDefault("scene_editor_ui_popup")
        val uiBaseBgSurface  = engine.gfx.getSurfaceOrDefault("scene_editor_ui_base_bg")
        val uiPopupBgSurface = engine.gfx.getSurfaceOrDefault("scene_editor_ui_popup_bg")

        viewportInteraction?.onRender(engine, viewportContext)

        rootUI.render(engine, uiBaseSurface, renderPopup = false)
        rootUI.renderPopup(engine, uiPopupSurface)

        renderFrostedGlass(engine, uiBaseBgSurface, rootUI)
        renderFrostedGlass(engine, uiPopupBgSurface, rootUI, onlyPopups = true)
    }

    private fun renderFrostedGlass(engine: PulseEngine, surface: Surface, node: UiElement, onlyPopups: Boolean = false)
    {
        if (node.hidden) return

        var onlyPopups = onlyPopups
        val isNodePopup = node === node.parent?.popup
        val isTransparent = (node is Panel && node.color.alpha == 0f)

        if (!isTransparent && node is Panel && (!onlyPopups || isNodePopup))
        {
            FrostedGlassEffect.drawToTargetSurface(engine, surface, node.x.value, node.y.value, node.width.value, node.height.value, node.getCornerRadius())
            onlyPopups = true // Only draw popups after first panel
        }

        node.children.forEachFast { renderFrostedGlass(engine, surface, it, onlyPopups) }

        if (onlyPopups && node.popup != null)
            renderFrostedGlass(engine, surface, node.popup!!, onlyPopups = true)
    }

    private fun onSaveAs(engine: PulseEngine)
    {
        if (engine.scene.state == SceneState.RUNNING)
            engine.scene.stop()

        scope.launch(context = Dispatchers.IO)
        {
            FileChooser.showSaveFileDialog(engine.config.saveDirectory)
            {
                sceneFileToSaveAs = it + if (!it.endsWith(".scn")) ".scn" else ""
            }
        }
    }

    private fun onLoad(engine: PulseEngine)
    {
        if (engine.scene.state != SceneState.RUNNING)
            saveActiveEditorScene(engine)

        scope.launch(context = Dispatchers.IO)
        {
            FileChooser.showMultipleFileSelectionDialog(engine.config.saveDirectory)
            {
                sceneFilesToLoad.addAll(it)
            }
        }
    }

    private fun onNewScene(engine: PulseEngine)
    {
        if (engine.scene.state == SceneState.RUNNING)
        {
            engine.scene.stop()
            saveActiveEditorScene(engine)
        }

        scope.launch(context = Dispatchers.IO)
        {
            FileChooser.showSaveFileDialog(engine.config.saveDirectory)
            {
                sceneFileToCreate = it
            }
        }
    }

    private fun stopEditorAndStartGame(engine: PulseEngine)
    {
        viewportInteraction?.onEditorDeactivated(engine, viewportContext)
        stop() // Stop editor service
        rememberEntitySelection()

        resetUI(engine)
        engine.input.setCursorType(ARROW)

        if (engine.scene.state == SceneState.STOPPED)
        {
            saveActiveEditorScene(engine)
            engine.scene.start()
        }
    }

    private fun stopGameAndStartEditor(engine: PulseEngine)
    {
        if (engine.scene.state != SceneState.STOPPED)
        {
            engine.scene.stop()
            engine.scene.reload()
            ensureActiveEditorScene(engine, replaceSceneWithSameFile = true)
        }

        viewportInteraction?.onEditorActivated(engine, viewportContext)
        engine.input.setCursorMode(CursorMode.NORMAL)
        start() // Start editor service
    }

    ////////////////////////////// EDIT TOOLS  //////////////////////////////

    private fun SceneEntity.setDead(engine: PulseEngine)
    {
        this.set(DEAD)
        this.childIds?.forEachFast { engine.scene.getEntity(it)?.setDead(engine) }
    }

    private fun handleEntityCopying(engine: PulseEngine)
    {
        if (!engine.input.isPressed(LEFT_CONTROL) || !engine.input.isPressed(D))
        {
            isCopying = false
            return
        }

        if (isCopying)
            return

        val newEntities = engine.scene.addEntitiesFrom(engine.scene.activeScene, filter = Entities(entitySelection)) ?: emptyList()
        
        outliner?.addEntities(newEntities)

        if (newEntities.isNotEmpty())
            markActiveEditorSceneDirty(engine)

        isCopying = true
    }

    private fun copySelectedEntitiesToClipboard(engine: PulseEngine)
    {
        engine.scene.activeScene
            .getEntities(Entities(entitySelection), includeChildren = true)
            .let(engine.data::serializeToJson)
            ?.let(engine.input::setClipboard)
    }

    private fun pasteEntitiesFromClipboard(engine: PulseEngine)
    {
        val targetScene = engine.scene.activeScene
        engine.input.getClipboard { json ->

            if (!isRunning || engine.scene.activeScene !== targetScene)
                return@getClipboard

            engine.scene.addEntitiesFrom(json)?.let { entities ->
                outliner?.addEntities(entities)
                selectEntities(engine, entities)
                if (entities.isNotEmpty())
                    markActiveEditorSceneDirty(engine)
            }
        }
    }

    private fun isControlPressed(engine: PulseEngine) =
        engine.input.isPressed(LEFT_CONTROL) || engine.input.isPressed(RIGHT_CONTROL)

    private fun hasFocusedInputField(engine: PulseEngine) =
        ::rootUI.isInitialized &&
        rootUI.firstElementOrNull { it is InputField && engine.input.hasFocus(it.area) } != null

    private fun createNewEntity(engine: PulseEngine, type: KClass<out SceneEntity>)
    {
        val entity = type.createInstance()
        if (entity is Spatial2D)
        {
            val w = engine.window.width
            val h = engine.window.height
            val spawnPos = activeCamera.screenPosToWorldPos(w * 0.5f, h * 0.5f, 0f, w, h)
            entity.x = spawnPos.x
            entity.y = spawnPos.y
        }
        entity.setPrimitiveProperty("textureName", "crate")
        engine.scene.addEntity(entity)
        outliner?.addEntities(listOf(entity))
        selectSingleEntity(engine, entity)
        markActiveEditorSceneDirty(engine)
    }

    fun selectEntities(engine: PulseEngine, entities: List<SceneEntity>)
    {
        if (entities.isEmpty())
        {
            clearEntitySelection()
            outliner?.selectEntities(emptyList())
        }
        else if (entities.size == 1)
        {
            selectSingleEntity(engine, entities[0])
        }
        else
        {
            clearEntitySelection()
            entities.forEachFast { addEntityToSelection(it) }
            outliner?.selectEntities(entities)
        }
    }

    fun selectSingleEntity(engine: PulseEngine, entity: SceneEntity)
    {
        clearEntitySelection()
        addEntityToSelection(entity)
        outliner?.selectEntities(entitySelection)

        val entityName = entity::class.getName()
        val propertyGroups = entity::class.memberProperties
            .filter { entity.getPropInfo(it)?.hidden != true }
            .groupBy { entity.getPropGroup(it)?.takeIf { it.isNotEmpty() } ?: entityName }
            .toList()
            .sortedBy { it.first } // Alphabetic order
            .sortedBy { it.first != entityName } // Entity type first

        for ((group, props) in propertyGroups)
        {
            val onChanged = { propName: String, lastValue: Any?, _: Any? ->
                if (propName == SceneEntity::parentId.name)
                {
                    val newParentId = entity.parentId
                    val lastParentId = when (lastValue)
                    {
                        is Long -> lastValue
                        is String -> lastValue.toLongOrNull() ?: INVALID_ID
                        else -> INVALID_ID
                    }

                    engine.scene.getEntity(lastParentId)?.removeChild(entity)
                    engine.scene.getEntity(newParentId)?.addChild(entity)
                    outliner?.removeEntities(listOf(entity))
                    outliner?.addEntities(listOf(entity))
                }
                outliner?.updateEntityProperty(entity, propName)
                markActiveEditorSceneDirty(engine)
                Unit
            }

            val headerId = "header_$group"
            val isCollapsed = headerId in collapsedPropertyHeaders
            val propertyRows = props
                .sortedBy { entity.getPropInfo(it)?.i ?: 0 }
                .filterIsInstance<KMutableProperty<*>>()
                .filter { it.isEditable() }
                .map { prop -> prop to uiFactory.createPropertyUI(entity, prop, onChanged) }

            if (propertyRows.isNotEmpty())
            {
                val headerButton = uiFactory.createCategoryHeader(
                    label = group,
                    isCollapsed = isCollapsed,
                    onClicked = {
                        propertyRows.forEachFast { (_, ui) -> ui.first.hidden = !ui.first.hidden }
                        if (it.isPressed) collapsedPropertyHeaders.add(headerId) else collapsedPropertyHeaders.remove(headerId)
                    }
                )
                inspectorUI.addChildren(headerButton)
            }

            for ((prop, ui) in propertyRows)
            {
                val (propertyPanel, inputElement) = ui
                propertyPanel.hidden = isCollapsed
                inspectorUI.addChildren(propertyPanel)
                entityPropertyUiRows[prop.name] = inputElement
            }
        }

        // Add bottom padding to the last property row
        inspectorUI.children.lastOrNull()?.let { it.padding.bottom = it.padding.top }
    }

    fun deleteSelectedEntities(engine: PulseEngine)
    {
        if (entitySelection.isEmpty())
            return

        entitySelection.forEachFast { it.setDead(engine) }
        outliner?.removeEntities(entitySelection)
        viewportInteraction?.reset(engine, viewportContext)
        clearEntitySelection()
        markActiveEditorSceneDirty(engine)
    }

    private fun updateEntityPropertiesPanel(propName: String, value: Any)
    {
        (entityPropertyUiRows[propName] as? InputField)?.text = value.toString()
    }

    private fun updateSceneSystemProperties(engine: PulseEngine)
    {
        val openSystems = systemPropertiesUI.children
            .filterIsInstance<Button>()
            .filter { !it.isPressed }
            .mapNotNull { it.id }

        systemPropertiesUI.clearChildren()
        for (system in engine.scene.activeScene.systems)
        {
            val isHidden = system::class.simpleName isNotIn openSystems
            val props = uiFactory.createSystemProperties(system, isHidden = isHidden, onClose = { props ->
                system.onDestroy(engine)
                engine.scene.removeSystem(system)
                systemPropertiesUI.removeChildren(*props.toTypedArray())
                markActiveEditorSceneDirty(engine)
            }, onChanged = { markActiveEditorSceneDirty(engine) })
            systemPropertiesUI.addChildren(*props.toTypedArray())
        }
    }

    ////////////////////////////// UTILS //////////////////////////////

    private fun clearEntitySelection()
    {
        entitySelection.forEachFast { it.setNot(SELECTED) }
        entitySelection.clear()
        inspectorUI.clearChildren()
        entityPropertyUiRows.clear()
    }

    private fun addEntityToSelection(entity: SceneEntity)
    {
        entitySelection.add(entity)
        entity.set(SELECTED)
    }

    private fun initializeEntities(engine: PulseEngine)
    {
        val selectedIds = selectedEntityIdsBeforePlay.toHashSet()
        val restoredSelection = ArrayList<SceneEntity>(selectedIds.size)
        engine.scene.forEachEntity()
        {
            if (it.id in selectedIds)
                restoredSelection.add(it)
            if (it is PhysicsEntity2D)
                it.init(engine)
        }

        selectedEntityIdsBeforePlay = emptyList()
        if (restoredSelection.isNotEmpty())
            selectEntities(engine, restoredSelection)
    }

    private fun rememberEntitySelection()
    {
        selectedEntityIdsBeforePlay = entitySelection.map { it.id }
    }

    private fun SceneEntity.onMovedScaledOrRotated(engine: PulseEngine)
    {
        if (this is PhysicsBody2D)
            this.init(engine)
    }

    private fun resetUI(engine: PulseEngine)
    {
        isCopying = false
        viewportInteraction?.reset(engine, viewportContext)
        clearEntitySelection()
    }

    override fun onDestroy(engine: PulseEngine)
    {
        viewportInteraction?.onDestroy(engine, viewportContext)

        if (shouldPersistEditorLayout)
            dockingUI.saveLayout(engine, "/editor_layout.cfg")
    }

    internal fun selectedEntities() = entitySelection

    internal fun isGridVisible() = showGrid

    internal fun clearViewportSelection()
    {
        clearEntitySelection()
        outliner?.selectEntities(emptyList())
    }

    internal fun notifyTransformChanged(engine: PulseEngine, entity: SceneEntity, propertyNames: Array<out String>)
    {
        propertyNames.forEach { name ->
            val property = entity::class.memberProperties.firstOrNull { it.name == name } ?: return@forEach
            updateEntityPropertiesPanel(name, property.getter.call(entity) ?: return@forEach)
        }
        entity.onMovedScaledOrRotated(engine)
        markActiveEditorSceneDirty(engine)
    }

    private fun createSceneTabModels(engine: PulseEngine): List<EditorSceneTab>
    {
        val activeScene = engine.scene.activeScene
        val showCloseButtons = editorScenes.size > 1
        return editorScenes.map { editorScene ->
            val labelText = editorScene.scene.fileName
                .substringAfterLast("/")
                .substringAfterLast("\\") + if (editorScene.dirty) " *" else ""
            val onClosed: (() -> Unit)? =
                if (showCloseButtons) ({ closeEditorScene(engine, editorScene) })
                else null

            EditorSceneTab(
                label = labelText,
                selected = editorScene.scene === activeScene,
                onSelected = { switchToEditorScene(engine, editorScene) },
                onClosed = onClosed
            )
        }
    }

    private fun rebuildSceneTabs(engine: PulseEngine)
    {
        if (::sceneTabsUI.isInitialized)
            uiFactory.populateSceneTabsUI(engine, sceneTabsUI, createSceneTabModels(engine))
    }

    private fun openEditorScene(engine: PulseEngine, fileName: String)
    {
        editorScenes.firstOrNull { it.scene.fileName == fileName }?.let() 
        {
            switchToEditorScene(engine, it)
            return
        }

        val scene = engine.scene.load(fileName) ?: return
        val editorScene = EditorScene(scene)
        editorScenes.add(editorScene)
        switchToEditorScene(engine, editorScene)
    }

    private fun switchToEditorScene(engine: PulseEngine, editorScene: EditorScene)
    {
        if (engine.scene.state != SceneState.STOPPED || editorScene.scene === engine.scene.activeScene)
            return

        clearEntitySelection()
        engine.scene.setActive(editorScene.scene, disposePrevious = false)
        outliner?.activeSceneChanged()
        lastSceneHashCode = -1
        rebuildSceneTabs(engine)
    }

    private fun closeActiveEditorScene(engine: PulseEngine)
    {
        activeEditorScene(engine)?.let { closeEditorScene(engine, it) }
    }

    private fun closeEditorScene(engine: PulseEngine, editorScene: EditorScene)
    {
        if (engine.scene.state != SceneState.STOPPED || editorScenes.size <= 1)
            return

        val index = editorScenes.indexOf(editorScene)
        if (index < 0) 
            return

        val wasActive = editorScene.scene === engine.scene.activeScene
        if (editorScene.dirty)
        {
            if (wasActive)
            {
                saveActiveEditorScene(engine)
            }
            else
            {
                editorScene.scene.optimizeCollections()
                engine.data.saveObject(editorScene.scene, editorScene.scene.fileName, editorScene.scene.fileFormat)
                editorScene.dirty = false
            }
        }

        editorScenes.removeAt(index)
        if (wasActive)
        {
            val next = editorScenes[index.coerceAtMost(editorScenes.lastIndex)]
            engine.scene.setActive(next.scene, disposePrevious = true)
            outliner?.activeSceneChanged()
            lastSceneHashCode = -1
        }
        else editorScene.scene.clearAll()

        rebuildSceneTabs(engine)
    }

    private fun saveActiveEditorScene(engine: PulseEngine)
    {
        engine.scene.save()
        activeEditorScene(engine)?.dirty = false
        rebuildSceneTabs(engine)
    }

    private fun markActiveEditorSceneDirty(engine: PulseEngine)
    {
        activeEditorScene(engine)?.let()
        {
            if (!it.dirty) 
                rebuildSceneTabs(engine)
            it.dirty = true
        }
    }

    private fun activeEditorScene(engine: PulseEngine) =
        editorScenes.firstOrNull { it.scene === engine.scene.activeScene }

    private fun loadInitialEditorScenes(engine: PulseEngine)
    {
        ensureActiveEditorScene(engine)
        initialSceneFiles
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { fileName -> editorScenes.any { it.scene.fileName == fileName } }
            .mapNotNull { engine.scene.load(it) }
            .forEach { editorScenes.add(EditorScene(it)) }
    }

    private fun ensureActiveEditorScene(engine: PulseEngine, replaceSceneWithSameFile: Boolean = false)
    {
        val activeScene = engine.scene.activeScene
        if (editorScenes.any { it.scene === activeScene })
            return

        if (replaceSceneWithSameFile)
        {
            editorScenes.firstOrNull { it.scene.fileName == activeScene.fileName }?.let {
                it.scene = activeScene
                it.dirty = false
                rebuildSceneTabs(engine)
                return
            }
        }

        editorScenes.add(EditorScene(activeScene))
        rebuildSceneTabs(engine)
    }

    private data class EditorScene(
        var scene: Scene,
        var dirty: Boolean = false
    )
}