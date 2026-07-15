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
import no.njoh.pulseengine.modules.ui.elements.InputField
import no.njoh.pulseengine.modules.ui.UiElement
import no.njoh.pulseengine.modules.ui.layout.RowPanel
import no.njoh.pulseengine.modules.ui.layout.VerticalPanel
import no.njoh.pulseengine.modules.ui.layout.docking.DockingPanel
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.input.Key.*
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.EDITABLE
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SELECTED
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.modules.physics.PhysicsEntity
import no.njoh.pulseengine.modules.physics.bodies.PhysicsBody
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
import no.njoh.pulseengine.modules.editor.EditorUtil.duplicateAndInsertEntities
import no.njoh.pulseengine.modules.editor.EditorUtil.getName
import no.njoh.pulseengine.modules.editor.EditorUtil.getPropGroup
import no.njoh.pulseengine.modules.editor.EditorUtil.getPropInfo
import no.njoh.pulseengine.modules.editor.EditorUtil.isEditable
import no.njoh.pulseengine.modules.editor.EditorUtil.setPrimitiveProperty
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.*

class SceneEditor(
    val uiFactory: UiElementFactory = UiElementFactory(),
    val viewportInteraction: ViewportInteraction? = ViewportInteraction2D()
): Service() {

    // UI
    lateinit var viewportArea: FocusArea
    lateinit var rootUI: VerticalPanel
    lateinit var inspectorUI: RowPanel
    lateinit var systemPropertiesUI: RowPanel
    lateinit var dockingUI: DockingPanel
    lateinit var viewportContext: ViewportContext

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
    private var sceneFileToLoad: String? = null
    private var sceneFileToCreate: String? = null
    private var sceneFileToSaveAs: String? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Copying
    private var isCopying = false
    private var prevSelectedEntityId: Long? = null

    // Loading and saving
    private var shouldPersistEditorLayout = false
    private var lastSaveLoadDirectory = ""

    override fun onCreate(engine: PulseEngine)
    {
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

        // Delete selected entities on key press and save scene on CTRL + S
        engine.input.setOnKeyPressed()
        {
            if (isRunning && it == DELETE && engine.input.hasFocus(viewportArea))
                deleteSelectedEntities(engine)
            if (isRunning && it == Key.S && engine.input.isPressed(LEFT_CONTROL))
                engine.scene.save()
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
                MenuBarItem("New...") { onNewScene(engine) },
                MenuBarItem("Open...") { onLoad(engine) },
                MenuBarItem("Save") { engine.scene.save() },
                MenuBarItem("Save as...") { onSaveAs(engine) }
            )),
            MenuBarButton("View", listOf(
                MenuBarItem("Inspector") { createInspectorWindow() },
                MenuBarItem("Outliner") { createOutlinerWindow(engine) },
                MenuBarItem("Scene systems") { createSceneSystemsPropertyWindow(engine) },
                MenuBarItem("Viewport") { createViewportWindow(engine) },
                MenuBarItem("Grid") {
                    showGrid = !showGrid
                },
                MenuBarItem("Reset") {
                    createSceneEditorUI(engine)
                    showGrid = true
                    viewportInteraction?.resetCamera(engine, viewportContext)
                }
            )),
            MenuBarButton("Run", listOf(
                MenuBarItem("Start") { stopEditorAndStartGame(engine) },
                MenuBarItem("Stop") { stopGameAndStartEditor(engine) },
                MenuBarItem("Pause") { engine.scene.pause() }
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
        rootUI.addChildren(menuBar, dockingUI, footer)
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
        val sceneSystemPropertiesUi = uiFactory.createSystemPropertiesPanelUI(engine, systemPropertiesUI)
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

        val window = uiFactory.createWindowUI(title = "Outliner", iconName = "LIST", onClosed = { outliner = null })
        window.body.addChildren(outliner!!.ui)
        dockingUI.insertLeft(window)
    }

    private fun createInspectorWindow()
    {
        if (dockingUI.findElement("Inspector") != null)
            return // Already exists

        val inspectorWindow = uiFactory.createWindowUI("Inspector", "CUBE")
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
        sceneFileToLoad?.let()
        {
            engine.scene.loadAndSetActive(it)
            sceneFileToLoad = null
        }

        sceneFileToCreate?.let()
        {
            engine.scene.createEmptyAndSetActive(it)
            engine.scene.addSystem(EntityUpdater().also { it.init(engine) })
            engine.scene.addSystem(EntityRendererImpl().also { it.init(engine) })
            engine.scene.save()
            sceneFileToCreate = null
        }

        sceneFileToSaveAs?.let()
        {
            engine.scene.saveAs(fileName = it, updateActiveScene = true)
            sceneFileToSaveAs = null
        }

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
                engine.scene.save()
                engine.scene.start()
            }
            else
            {
                engine.scene.stop()
                engine.scene.reload()
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
            FrostedGlassEffect.drawToTargetSurface(engine, surface, node.x.value, node.y.value, node.width.value, node.height.value, node.cornerRadius.value)
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
            engine.scene.save()

        scope.launch(context = Dispatchers.IO)
        {
            FileChooser.showFileSelectionDialog(engine.config.saveDirectory)
            {
                sceneFileToLoad = it
            }
        }
    }

    private fun onNewScene(engine: PulseEngine)
    {
        if (engine.scene.state == SceneState.RUNNING)
        {
            engine.scene.stop()
            engine.scene.save()
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
        prevSelectedEntityId = entitySelection.firstOrNull()?.id

        resetUI(engine)
        engine.input.setCursorType(ARROW)

        if (engine.scene.state == SceneState.STOPPED)
        {
            engine.scene.save()
            engine.scene.start()
        }
    }

    private fun stopGameAndStartEditor(engine: PulseEngine)
    {
        if (engine.scene.state != SceneState.STOPPED)
        {
            engine.scene.stop()
            engine.scene.reload()
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

        val newEntities = duplicateAndInsertEntities(engine, entitySelection)
        outliner?.addEntities(newEntities)

        isCopying = true
    }

    private fun createNewEntity(engine: PulseEngine, type: KClass<out SceneEntity>)
    {
        val entity = type.createInstance()
        if (entity is Spatial)
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
                    val lastParentId = (lastValue as? String)?.toLongOrNull() ?: INVALID_ID

                    engine.scene.getEntity(lastParentId)?.removeChild(entity)
                    engine.scene.getEntity(newParentId)?.addChild(entity)
                    outliner?.removeEntities(listOf(entity))
                    outliner?.addEntities(listOf(entity))
                }
                outliner?.updateEntityProperty(entity, propName)
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
            })
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
        engine.scene.forEachEntity()
        {
            if (prevSelectedEntityId != null && prevSelectedEntityId == it.id)
                selectSingleEntity(engine, it)
            if (it is PhysicsEntity)
                it.init(engine)
        }
    }

    private fun SceneEntity.onMovedScaledOrRotated(engine: PulseEngine)
    {
        if (this is PhysicsBody)
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
    }
}