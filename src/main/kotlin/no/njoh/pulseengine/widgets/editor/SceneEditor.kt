package no.njoh.pulseengine.widgets.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.input.CursorType.*
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.console.CommandResult
import no.njoh.pulseengine.core.graphics.Camera
import no.njoh.pulseengine.core.graphics.api.Attachment
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.graphics.api.Multisampling
import no.njoh.pulseengine.modules.gui.UiUtil.findElementById
import no.njoh.pulseengine.modules.gui.elements.InputField
import no.njoh.pulseengine.modules.gui.UiElement
import no.njoh.pulseengine.modules.gui.layout.RowPanel
import no.njoh.pulseengine.modules.gui.layout.VerticalPanel
import no.njoh.pulseengine.modules.gui.layout.docking.DockingPanel
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.input.Key.*
import no.njoh.pulseengine.core.input.Mouse
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.EDITABLE
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SELECTED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.physics.PhysicsEntity
import no.njoh.pulseengine.modules.physics.bodies.PhysicsBody
import no.njoh.pulseengine.core.shared.utils.*
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.widget.Widget
import no.njoh.pulseengine.modules.gui.UiParams.UI_SCALE
import no.njoh.pulseengine.modules.gui.elements.Button
import no.njoh.pulseengine.modules.gui.layout.WindowPanel
import no.njoh.pulseengine.widgets.editor.EditorUtil.getName
import no.njoh.pulseengine.widgets.editor.EditorUtil.getPropInfo
import no.njoh.pulseengine.widgets.editor.EditorUtil.isPrimitiveValue
import no.njoh.pulseengine.widgets.editor.EditorUtil.isEditable
import no.njoh.pulseengine.widgets.editor.EditorUtil.isPrimitiveArray
import no.njoh.pulseengine.widgets.editor.EditorUtil.setArrayProperty
import no.njoh.pulseengine.widgets.editor.EditorUtil.setPrimitiveProperty
import org.joml.Vector3f
import kotlin.math.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.*

class SceneEditor(
    val uiFactory: UiElementFactory = UiElementFactory(),
    var enableViewportInteractions: Boolean = true
): Widget {

    override var isRunning = false

    // UI
    lateinit var viewportArea: FocusArea
    lateinit var rootUI: VerticalPanel
    lateinit var inspectorUI: RowPanel
    lateinit var systemPropertiesUI: RowPanel
    lateinit var dockingUI: DockingPanel

    private var entityPropertyUiRows = mutableMapOf<String, UiElement>()
    private var collapsedPropertyHeaders = mutableListOf<String>()
    private var updateFooterCallback: (totalEntities: Int, selectedEntities: Int, sceneName: String) -> Unit = { _,_,_ -> }
    private var showGrid = true
    private var outliner: Outliner? = null

    // Camera
    private val cameraController = Camera2DController(Mouse.MIDDLE, smoothing = 0f)
    private lateinit var activeCamera: Camera
    private lateinit var storedCameraState: CameraState

    // Scene
    private var lastSceneHashCode = -1
    private val entitySelection = mutableListOf<SceneEntity>()
    private var sceneFileToLoad: String? = null
    private var sceneFileToCreate: String? = null
    private var sceneFileToSaveAs: String? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Moving and copying
    private var isMoving = false
    private var isCopying = false

    // Rotation
    private var isRotating = false
    private var mouseStartAngle = 0f
    private var entityStartAngle = 0f
    private var entityStartHeight = 0f
    private var entityStartWidth = 0f

    // Resizing
    private var isResizingVertically = false
    private var isResizingHorizontally = false
    private var xResizeDirection = 0f
    private var yResizeDirection = 0f
    private var resizeIconAngle = 0f
    private var xMouseStart = 0f
    private var yMouseStart = 0f

    // Selecting
    private var isSelecting = false
    private var xStartSelect = 0f
    private var yStartSelect = 0f
    private var xEndSelect = 0f
    private var yEndSelect = 0f
    private var prevSelectedEntityId: Long? = null

    // Loading and saving
    private var shouldPersistEditorLayout = false
    private var lastSaveLoadDirectory = ""

    override fun onCreate(engine: PulseEngine)
    {
        // Load editor config
        engine.config.load("/pulseengine/config/editor_default.cfg")
        UI_SCALE = engine.config.getFloat("uiScale") ?: engine.window.scale

        // Set editor data
        viewportArea = FocusArea(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
        activeCamera = engine.gfx.mainCamera
        storedCameraState = CameraState.from(activeCamera)
        shouldPersistEditorLayout = engine.config.getBool("persistEditorLayout") ?: false
        isRunning = engine.config.getBool("openEditorOnStart") ?: false
        lastSaveLoadDirectory = engine.data.saveDirectory

        // Create separate render surface for editor UI
        engine.gfx.createSurface(
            name = "scene_editor_foreground",
            zOrder = -90,
            attachments = listOf(Attachment.COLOR_TEXTURE_0, Attachment.DEPTH_STENCIL_BUFFER),
            multisampling = Multisampling.MSAA16
        )

        // Background surface for grid
        engine.gfx.createSurface(
            name = "scene_editor_background",
            zOrder = 20,
            camera = activeCamera,
            backgroundColor = Color(0.043f, 0.047f, 0.054f, 0f)
        )

        // Load editor icon font
        engine.asset.loadFont("/pulseengine/assets/editor_icons.ttf", uiFactory.style.iconFontName)

        // Register a console command to toggle editor visibility
        engine.console.registerCommand("showSceneEditor")
        {
            isRunning = !isRunning
            if (!isRunning) play(engine) else stop(engine)
            CommandResult("", showCommand = false)
        }

        // Delete selected entities on key press
        engine.input.setOnKeyPressed()
        {
            if (it == DELETE && isRunning && engine.input.hasFocus(viewportArea))
                deleteSelectedEntities(engine)
        }

        // React on scale changes
        engine.window.setOnScaleChanged()
        {
            createSceneEditorUI(engine)
        }

        // Create and populate editor with UI
        createSceneEditorUI(engine)
    }

    private fun createSceneEditorUI(engine: PulseEngine)
    {
        // Set UI scaling
        UI_SCALE = engine.config.getFloat("uiScale") ?: engine.window.scale
        cameraController.scrollSpeed = 40f * UI_SCALE

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
                    storedCameraState.apply { reset() }.loadInto(engine.gfx.mainCamera)
                }
            )),
            MenuBarButton("Run", listOf(
                MenuBarItem("Start") { play(engine) },
                MenuBarItem("Stop") { stop(engine) },
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
        if (dockingUI.findElementById("Scene Systems") != null)
            return // Already exists

        updateSceneSystemProperties(engine)
        val sceneSystemPropertiesUi = uiFactory.createSystemPropertiesPanelUI(engine, systemPropertiesUI)
        val sceneSystemWindow = uiFactory.createWindowUI("Scene Systems", "GEARS")
        sceneSystemWindow.body.addChildren(sceneSystemPropertiesUi)
        dockingUI.insertRight(sceneSystemWindow)
    }

    private fun createOutlinerWindow(engine: PulseEngine)
    {
        if (dockingUI.findElementById("Outliner") != null)
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
        if (dockingUI.findElementById("Inspector") != null)
            return // Already exists

        val inspectorWindow = uiFactory.createWindowUI("Inspector", "CUBE")
        val propertyPanel = uiFactory.createScrollableSectionUI(inspectorUI)
        inspectorWindow.body.addChildren(propertyPanel)

        val propWindow = dockingUI.findElementById("Outliner")
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
            resetUI()
            updateSceneSystemProperties(engine)
            initializeEntities(engine)
            outliner?.reloadEntitiesFromActiveScene()
            lastSceneHashCode = engine.scene.activeScene.hashCode()
        }

        if (enableViewportInteractions && engine.scene.state == SceneState.STOPPED)
        {
            engine.input.setCursorType(ARROW)
            cameraController.update(engine, activeCamera, enableScrolling = engine.input.hasHoverFocus(viewportArea))

            if (entitySelection.size == 1)
                entitySelection.first().handleEntityTransformation(engine)

            handleEntityCopying(engine)
            handleEntitySelection(engine)
            handleEntityMoving(engine)
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
        if (showGrid)
            renderGrid(engine.gfx.getSurfaceOrDefault("scene_editor_background"))

        val foregroundSurface = engine.gfx.getSurfaceOrDefault("scene_editor_foreground")

        if (enableViewportInteractions)
        {
            renderEntityIconAndGizmo(foregroundSurface, engine)
            renderSelectionRectangle(foregroundSurface)
        }

        rootUI.render(engine, foregroundSurface)
    }

    private fun renderEntityIconAndGizmo(surface: Surface2D, engine: PulseEngine)
    {
        val showResizeDots = (entitySelection.size == 1)
        entitySelection.forEachFast()
        {
            if (it.isNot(HIDDEN) && it.isSet(EDITABLE))
                it.renderGizmo(surface, showResizeDots)
        }

        engine.scene.forEachEntityTypeList { entities ->
            entities[0]::class.findAnnotation<ScnIcon>()?.let { annotation ->
                if (annotation.showInViewport && entities[0] is Spatial)
                {
                    val size = annotation.size
                    val texture = engine.asset.getOrNull<Texture>(annotation.textureAssetName)
                    val font = engine.asset.getOrNull<Font>(uiFactory.style.iconFontName)
                    val iconChar = uiFactory.style.icons[annotation.iconName]
                    if (texture != null || (font != null && iconChar != null))
                    {
                        surface.setDrawColor(Color.WHITE)
                        entities.forEachFast()
                        {
                            if (it.isNot(HIDDEN) && it.isSet(EDITABLE))
                            {
                                it as Spatial
                                val pos = engine.gfx.mainCamera.worldPosToScreenPos(it.x, it.y)
                                if (texture != null)
                                    surface.drawTexture(texture, pos.x, pos.y, size, size, 0f, 0.5f, 0.5f)
                                else if (iconChar != null)
                                    surface.drawText(iconChar, pos.x, pos.y, font, size, xOrigin = 0.5f, yOrigin = 0.5f)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onSaveAs(engine: PulseEngine)
    {
        if (engine.scene.state == SceneState.RUNNING)
            engine.scene.stop()

        scope.launch(context = Dispatchers.IO)
        {
            FileChooser.showSaveFileDialog("scn", engine.data.saveDirectory) { filePath ->
                sceneFileToSaveAs = filePath + if (!filePath.endsWith(".scn")) ".scn" else ""
            }
        }
    }

    private fun onLoad(engine: PulseEngine)
    {
        if (engine.scene.state != SceneState.RUNNING)
            engine.scene.save()

        scope.launch(context = Dispatchers.IO)
        {
            FileChooser.showFileSelectionDialog("scn", engine.data.saveDirectory) { filePath ->
                sceneFileToLoad = filePath
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
            FileChooser.showSaveFileDialog("scn", engine.data.saveDirectory) { filePath ->
                sceneFileToCreate = filePath
            }
        }
    }

    private fun play(engine: PulseEngine)
    {
        isRunning = false
        storedCameraState.saveFrom(activeCamera)
        activeCamera.scale.set(1f)
        activeCamera.position.set(0f)
        activeCamera.rotation.set(0f)
        prevSelectedEntityId = entitySelection.firstOrNull()?.id

        resetUI()
        engine.input.setCursorType(ARROW)

        if (engine.scene.state == SceneState.STOPPED)
        {
            engine.scene.save()
            engine.scene.start()
        }
    }

    private fun stop(engine: PulseEngine)
    {
        isRunning = true
        storedCameraState.loadInto(activeCamera)

        if (engine.scene.state != SceneState.STOPPED)
        {
            engine.scene.stop()
            engine.scene.reload()
        }
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

        if (!isMoving || isCopying)
            return

        duplicateSelectedEntities(engine)

        isCopying = true
    }

    private fun handleEntitySelection(engine: PulseEngine)
    {
        // Select all with CTRL + A
        if (engine.input.wasClicked(A) && engine.input.isPressed(LEFT_CONTROL))
        {
            clearEntitySelection()
            engine.scene.forEachEntity()
            {
                it.set(SELECTED)
                entitySelection.add(it)
            }
            outliner?.selectEntities(entitySelection)
        }

        val xMouse = engine.input.xWorldMouse
        val yMouse = engine.input.yWorldMouse

        // Select / deselect single entity
        if (engine.input.wasClicked(Mouse.LEFT))
        {
            // Select entity
            if (!isMoving && !isSelecting && !isRotating && !isResizingVertically && !isResizingHorizontally)
            {
                var zMin = Float.MAX_VALUE
                var closestEntity: SceneEntity? = null
                engine.scene.forEachEntity()
                {
                    if (it is Spatial && it.z <= zMin && it.isInside(xMouse, yMouse) && it.isSet(EDITABLE) && it.isNot(HIDDEN))
                    {
                        zMin = it.z
                        closestEntity = it
                    }
                }

                closestEntity?.let()
                {
                    val isInSelection = (it in entitySelection)
                    if (engine.input.isPressed(LEFT_CONTROL))
                    {
                        if (isInSelection) removeEntityFromSelection(it) else addEntityToSelection(it)

                        if (entitySelection.size == 1)
                        {
                            selectSingleEntity(engine, entitySelection.first())
                        }
                        else
                        {
                            inspectorUI.clearChildren()
                            entityPropertyUiRows.clear()
                            outliner?.selectEntities(entitySelection)
                        }
                    }
                    else if (!isInSelection)
                    {
                        selectSingleEntity(engine, it)
                    }
                    isMoving = true
                }
            }
        }

        // Start multi selection
        if (engine.input.isPressed(Mouse.LEFT))
        {
            if (!isMoving && !isRotating && !isResizingVertically && !isResizingHorizontally)
            {
                xEndSelect = xMouse
                yEndSelect = yMouse

                if (!isSelecting)
                {
                    xStartSelect = xEndSelect
                    yStartSelect = yEndSelect
                    isSelecting = true
                }
            }
        }
        else isSelecting = false

        // Multi selection
        if (isSelecting)
        {
            val xStart = min(xStartSelect, xEndSelect)
            val yStart = min(yStartSelect, yEndSelect)
            val width  = abs(xEndSelect - xStartSelect)
            val height  = abs(yEndSelect - yStartSelect)
            val selectedEntity = entitySelection.firstOrNull()
            val prevEntityCount = entitySelection.size

            if (!engine.input.isPressed(LEFT_CONTROL))
            {
                // Reset selection only if CTRL is not pressed
                entitySelection.forEachFast { it.setNot(SELECTED) }
                entitySelection.clear()
            }

            engine.scene.forEachEntity()
            {
                if (it.isSet(EDITABLE) &&
                    it.isNot(HIDDEN) &&
                    it.isNot(SELECTED) &&
                    it is Spatial &&
                    it.isOverlapping(xStart, yStart, width, height)
                ) {
                    addEntityToSelection(it)
                }
            }

            if (entitySelection.size == 1)
            {
                if (entitySelection.first() !== selectedEntity)
                    selectSingleEntity(engine, entitySelection.first())
            }
            else
            {
                inspectorUI.clearChildren()
                if (entitySelection.size != prevEntityCount)
                    outliner?.selectEntities(entitySelection)
            }
        }
    }

    private fun handleEntityMoving(engine: PulseEngine)
    {
        // Nudge with arrow keys
        var xMove = 0f
        var yMove = 0f
        if (engine.input.wasClicked(UP)) yMove -= 1
        if (engine.input.wasClicked(DOWN)) yMove += 1
        if (engine.input.wasClicked(LEFT)) xMove -= 1
        if (engine.input.wasClicked(RIGHT)) xMove += 1
        if (xMove != 0f || yMove != 0f)
        {
            entitySelection.forEachFast()
            {
                if (it is Spatial)
                {
                    it.x += xMove
                    it.y += yMove
                    it.set(POSITION_UPDATED)
                    updateEntityPropertiesPanel(it::x.name, it.x)
                    updateEntityPropertiesPanel(it::y.name, it.y)
                }
            }
        }

        if (!isMoving) return

        if (!engine.input.isPressed(Mouse.LEFT))
        {
            engine.input.setCursorType(ARROW)
            entitySelection.forEachFast { it.onMovedScaledOrRotated(engine) }
            isMoving = false
            return
        }

        for (entity in entitySelection)
        {
            if (entity !is Spatial) continue

            entity.x += engine.input.xdMouse / activeCamera.scale.x
            entity.y += engine.input.ydMouse / activeCamera.scale.y
            entity.set(POSITION_UPDATED)

            updateEntityPropertiesPanel(entity::x.name, entity.x)
            updateEntityPropertiesPanel(entity::y.name, entity.y)
        }
    }

    private fun SceneEntity.handleEntityTransformation(engine: PulseEngine)
    {
        if (this !is Spatial) return

        val border = min(abs(width), abs(height)) * 0.1f
        val rotateArea = min(abs(width), abs(height)) * 0.2f

        val xDiff = engine.input.xWorldMouse - x
        val yDiff = engine.input.yWorldMouse - y
        val mouseEntityAngle = -atan2(yDiff, xDiff)
        val angle = mouseEntityAngle - (this.rotation / 180f * PI.toFloat())
        val len = sqrt(xDiff * xDiff + yDiff * yDiff)
        val xMouse = x + cos(angle) * len
        val yMouse = y + sin(angle) * len
        val padding = getGizmoPadding()
        val w = (abs(width) + padding * 2) / 2
        val h = (abs(height) + padding * 2) / 2

        val resizeBottom = xMouse >= x - w - border && xMouse <= x + w + border && yMouse >= y + h - border && yMouse <= y + h + border
        val resizeTop = xMouse >= x - w - border && xMouse <= x + w + border && yMouse >= y - h - border && yMouse <= y - h + border
        val resizeLeft = xMouse >= x - w - border && xMouse <= x - w + border && yMouse >= y - h - border && yMouse <= y + h + border
        val resizeRight = xMouse >= x + w - border && xMouse <= x + w + border && yMouse >= y - h - border && yMouse <= y + h + border

        val rotateTopLeft = xMouse >= x - w - rotateArea && xMouse <= x - w && yMouse >= y - h - rotateArea && yMouse <= y - h
        val rotateBottomLeft = xMouse >= x - w - rotateArea && xMouse <= x - w && yMouse >= y + h && yMouse <= y + h + rotateArea
        val rotateTopRight = xMouse >= x + w && xMouse <= x + w + rotateArea && yMouse >= y - h - rotateArea && yMouse <= y - h
        val rotateBottomRight = xMouse >= x + w && xMouse <= x + w + rotateArea && yMouse >= y + h && yMouse <= y + h + rotateArea

        if (engine.input.isPressed(Mouse.LEFT))
        {
            if (!isMoving && !isSelecting && !isRotating && !isResizingHorizontally && !isResizingVertically)
            {
                if (resizeBottom || resizeTop)
                {
                    isResizingVertically = true
                    entityStartWidth = width
                    entityStartHeight = height
                    xMouseStart = xMouse
                    yMouseStart = yMouse
                    yResizeDirection = (if (yMouse > y) -1f else 1f) * (if (height < 0) -1f else 1f)  // Inverts scaling when mouse is on opposite side or height is inverted
                    resizeIconAngle = getIconAngle(rotation, resizeLeft, resizeRight, resizeTop, resizeBottom)
                }

                if (resizeLeft || resizeRight)
                {
                    isResizingHorizontally = true
                    entityStartWidth = width
                    entityStartHeight = height
                    xMouseStart = xMouse
                    yMouseStart = yMouse
                    xResizeDirection = (if (xMouse > x) -1f else 1f) * (if (width < 0) -1f else 1f)
                    resizeIconAngle = getIconAngle(rotation, resizeLeft, resizeRight, resizeTop, resizeBottom)
                }

                if (!isResizingVertically && !isResizingHorizontally && (rotateTopLeft || rotateBottomLeft || rotateTopRight || rotateBottomRight))
                {
                    isRotating = true
                    entityStartAngle = this.rotation
                    mouseStartAngle = mouseEntityAngle
                }
            }
        }
        else
        {
            if (isResizingHorizontally || isResizingVertically || isRotating)
                onMovedScaledOrRotated(engine)

            isResizingVertically = false
            isResizingHorizontally = false
            isRotating = false
        }

        val ctrPressed = engine.input.isPressed(Key.LEFT_CONTROL)
        val shiftPressed = engine.input.isPressed(Key.LEFT_SHIFT)

        when
        {
            // Rotating
            isRotating -> {
                val diff = (mouseEntityAngle - mouseStartAngle) / PI.toFloat() * 180f
                rotation = if (ctrPressed)
                    ((entityStartAngle + diff).toInt() / 45 * 45).toFloat()
                else
                    entityStartAngle + diff
            }

            // Horizontal / corner resize, keep ratio
            isResizingHorizontally && shiftPressed -> {
                val xd = (xMouseStart - xMouse)
                val ratio = entityStartHeight / if (entityStartWidth == 0f) entityStartHeight else entityStartWidth
                width = entityStartWidth + xd * xResizeDirection
                height = entityStartHeight + ratio * xd * xResizeDirection
            }

            // Vertical resize, keep ratio
            isResizingVertically && shiftPressed -> {
                val yd = (yMouseStart - yMouse)
                val ratio = entityStartWidth / if (entityStartHeight == 0f) entityStartWidth else entityStartHeight
                height = entityStartHeight + yd * yResizeDirection
                width = entityStartWidth + ratio * yd * yResizeDirection
            }

            // Corner resize
            isResizingHorizontally && isResizingVertically -> {
                width = entityStartWidth + (xMouseStart - xMouse) * xResizeDirection
                height = entityStartHeight + (yMouseStart - yMouse) * yResizeDirection
            }

            // Horizontal resize
            isResizingHorizontally -> width = entityStartWidth + (xMouseStart - xMouse) * xResizeDirection

            // Vertical resize
            isResizingVertically -> height = entityStartHeight + (yMouseStart - yMouse) * yResizeDirection
        }

        // Update resize icon angle
        if (!isResizingHorizontally && !isResizingVertically)
            resizeIconAngle = getIconAngle(rotation, resizeLeft, resizeRight, resizeTop, resizeBottom)

        // Determine icon type
        val cursorType =
            if (!isRotating && (isResizingHorizontally || isResizingVertically || resizeBottom || resizeTop || resizeLeft || resizeRight))
                when (resizeIconAngle)
                {
                    in   0f ..  22f -> HORIZONTAL_RESIZE
                    in  22f ..  68f -> TOP_LEFT_RESIZE
                    in  68f .. 112f -> VERTICAL_RESIZE
                    in 112f .. 158f -> TOP_RIGHT_RESIZE
                    in 158f .. 202f -> HORIZONTAL_RESIZE
                    in 202f .. 248f -> TOP_LEFT_RESIZE
                    in 248f .. 292f -> VERTICAL_RESIZE
                    in 292f .. 338f -> TOP_RIGHT_RESIZE
                    in 338f .. 360f -> HORIZONTAL_RESIZE
                    else            -> ARROW
                }
            else if (isRotating || rotateTopLeft || rotateBottomLeft || rotateTopRight || rotateBottomRight)
                ROTATE
            else if (xMouse > x - w  && xMouse < x + w && yMouse > y - h && yMouse < y + h)
                MOVE
            else null // ARROW

        cursorType?.let { engine.input.setCursorType(it) }

        if (isRotating || isResizingHorizontally || isResizingVertically)
        {
            updateEntityPropertiesPanel(::rotation.name, rotation)
            updateEntityPropertiesPanel(::width.name, width)
            updateEntityPropertiesPanel(::height.name, height)
            set(SIZE_UPDATED)
            set(ROTATION_UPDATED)
        }
    }

    private fun getIconAngle(startAngle: Float, resizeLeft: Boolean, resizeRight: Boolean, resizeTop: Boolean, resizeBottom: Boolean): Float
    {
        var iconAngle = -startAngle - when
        {
            resizeTop && resizeRight -> 135
            resizeRight && resizeBottom -> 225
            resizeBottom && resizeLeft -> 315
            resizeLeft && resizeTop -> 45
            resizeRight -> 180
            resizeLeft -> 0
            resizeTop -> 90
            resizeBottom -> 270
            else -> 0
        }

        iconAngle %= 360
        if (iconAngle < 0)
            iconAngle += 360

        return iconAngle
    }

    private fun createNewEntity(engine: PulseEngine, type: KClass<out SceneEntity>)
    {
        val entity = type.createInstance()
        if (entity is Spatial)
        {
            val spawnPos = activeCamera.screenPosToWorldPos(engine.window.width * 0.5f, engine.window.height * 0.5f)
            entity.x = spawnPos.x
            entity.y = spawnPos.y
            entity.width = 512f
            entity.height = 512f
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
            .groupBy { entity.getPropInfo(it)?.group?.takeIf { it.isNotEmpty() } ?: entityName }
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

    fun duplicateSelectedEntities(engine: PulseEngine)
    {
        val copies = entitySelection.map { it.createCopy() }.toMutableList()
        val insertedCopies = mutableListOf<SceneEntity>()
        val idMapping = mutableMapOf<Long, Long>()

        // Insert copied entities in order of parents before children
        while (copies.isNotEmpty())
        {
            val copiesLeft = copies.size
            for (copy in copies)
            {
                if (copies.none { it.id == copy.parentId })
                {
                    copy.childIds = null
                    copy.parentId = idMapping[copy.parentId] ?: copy.parentId
                    copy.setNot(SELECTED)
                    val lastId = copy.id
                    val newId = engine.scene.addEntity(copy)
                    idMapping[lastId] = newId
                    copies.remove(copy)
                    insertedCopies.add(copy)
                    break
                }
            }

            if (copiesLeft == copies.size)
            {
                Logger.error("Failed to copy entities with circular dependencies! IDs: ${copies.map { it.id }}")
                return
            }
        }

        // Handle fields annotated with EntityRef
        for (entity in insertedCopies)
        {
            for (prop in entity::class.memberProperties)
            {
                if (prop is KMutableProperty<*> && prop.name != "parent" && prop.name != "childIds" && prop.hasAnnotation<EntityRef>())
                {
                    val ref = prop.getter.call(entity)
                    if (ref is Long)
                    {
                        idMapping[ref]?.let { newRef -> prop.setter.call(entity, newRef) }
                    }
                    else if (ref is LongArray && ref.isNotEmpty())
                    {
                        prop.setter.call(entity, LongArray(ref.size) { idMapping[ref[it]] ?: ref[it] })
                    }
                }
            }
        }

        outliner?.addEntities(insertedCopies)
    }

    fun deleteSelectedEntities(engine: PulseEngine)
    {
        if (entitySelection.isEmpty())
            return

        entitySelection.forEachFast { it.setDead(engine) }
        outliner?.removeEntities(entitySelection)
        isMoving = false
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
            val isHidden = system::class.simpleName !in openSystems
            val props = uiFactory.createSystemProperties(system, isHidden = isHidden, onClose = { props ->
                system.onDestroy(engine)
                engine.scene.removeSystem(system)
                systemPropertiesUI.removeChildren(*props.toTypedArray())
            })
            systemPropertiesUI.addChildren(*props.toTypedArray())
        }
    }

    ////////////////////////////// RENDERING  //////////////////////////////

    private fun renderSelectionRectangle(surface: Surface2D)
    {
        if (isSelecting)
        {
            val pos = activeCamera.worldPosToScreenPos(xStartSelect, yStartSelect)
            val x = pos.x
            val y = pos.y
            val w = (xEndSelect - xStartSelect) * activeCamera.scale.x
            val h = (yEndSelect - yStartSelect) * activeCamera.scale.y

            surface.setDrawColor(1f, 1f, 1f, 0.8f)
            surface.drawLine(x, y, x + w, y)
            surface.drawLine(x, y + h, x + w, y + h)
            surface.drawLine(x, y, x, y + h)
            surface.drawLine(x + w, y, x + w, y + h)
        }
    }

    private fun SceneEntity.renderGizmo(surface: Surface2D, showResizeDots: Boolean)
    {
        if (this !is Spatial) return

        val pos = activeCamera.worldPosToScreenPos(x, y)
        val padding = getGizmoPadding()
        val w = (width + padding * 2) * activeCamera.scale.x / 2f
        val h = (height + padding * 2) * activeCamera.scale.y / 2f
        val size = 4f * UI_SCALE
        val halfSize = size / 2f

        if (rotation != 0f)
        {
            val r = -this.rotation / 180f * PI.toFloat()
            val c = cos(r)
            val s = sin(r)
            val x0 = -w * c - h * s
            val y0 = -w * s + h * c
            val x1 =  w * c - h * s
            val y1 =  w * s + h * c

            surface.setDrawColor(1f, 1f, 1f, 0.8f)
            surface.drawLine(pos.x + x0, pos.y + y0, pos.x + x1, pos.y + y1)
            surface.drawLine(pos.x + x1, pos.y + y1, pos.x - x0, pos.y - y0)
            surface.drawLine(pos.x - x0, pos.y - y0, pos.x - x1, pos.y - y1)
            surface.drawLine(pos.x - x1, pos.y - y1, pos.x + x0, pos.y + y0)

            if (showResizeDots)
            {
                surface.setDrawColor(1f, 1f, 1f, 1f)
                surface.drawQuad(pos.x + x0 - halfSize, pos.y + y0 - halfSize, size, size)
                surface.drawQuad(pos.x + x1 - halfSize, pos.y + y1 - halfSize, size, size)
                surface.drawQuad(pos.x - x0 - halfSize, pos.y - y0 - halfSize, size, size)
                surface.drawQuad(pos.x - x1 - halfSize, pos.y - y1 - halfSize, size, size)
            }
        }
        else
        {
            surface.setDrawColor(1f, 1f, 1f, 0.8f)
            surface.drawLine(pos.x - w, pos.y - h, pos.x + w, pos.y - h)
            surface.drawLine(pos.x - w, pos.y + h, pos.x + w, pos.y + h)
            surface.drawLine(pos.x - w, pos.y - h, pos.x - w, pos.y + h)
            surface.drawLine(pos.x + w, pos.y - h, pos.x + w, pos.y + h)

            if (showResizeDots)
            {
                surface.setDrawColor(1f, 1f, 1f, 1f)
                surface.drawQuad(pos.x - w - halfSize, pos.y - h - halfSize, size, size)
                surface.drawQuad(pos.x + w - halfSize, pos.y - h - halfSize, size, size)
                surface.drawQuad(pos.x - w - halfSize, pos.y + h - halfSize, size, size)
                surface.drawQuad(pos.x + w - halfSize, pos.y + h - halfSize, size, size)
            }
        }
    }

    private fun renderGrid(surface: Surface2D)
    {
        val cellSize = 200
        val xStart = (activeCamera.topLeftWorldPosition.x.toInt() / cellSize - 2) * cellSize
        val yStart = (activeCamera.topLeftWorldPosition.y.toInt() / cellSize - 2) * cellSize
        val xEnd = (activeCamera.bottomRightWorldPosition.x.toInt() / cellSize + 1) * cellSize
        val yEnd = (activeCamera.bottomRightWorldPosition.y.toInt() / cellSize + 1) * cellSize

        val middleLineSize = 2f / activeCamera.scale.x
        val alpha = (activeCamera.scale.x + 0.2f).coerceIn(0.1f, 0.4f)
        val shade = 0.3f

        surface.setDrawColor(shade, shade, shade, alpha + 0.1f)
        for (x in xStart until xEnd step cellSize)
            if (x != 0 && x % 3 == 0) surface.drawLine(x.toFloat(), yStart.toFloat(), x.toFloat(), yEnd.toFloat())

        for (y in yStart until yEnd step cellSize)
            if (y != 0 && y % 3 == 0) surface.drawLine(xStart.toFloat(), y.toFloat(), xEnd.toFloat(), y.toFloat())

        surface.setDrawColor(shade, shade, shade, alpha)
        for (x in xStart until xEnd step cellSize)
            if (x != 0 && x % 3 != 0) surface.drawLine(x.toFloat(), yStart.toFloat(), x.toFloat(), yEnd.toFloat())

        for (y in yStart until yEnd step cellSize)
            if (y != 0 && y % 3 != 0) surface.drawLine(xStart.toFloat(), y.toFloat(), xEnd.toFloat(), y.toFloat())

        surface.setDrawColor(shade, shade, shade, alpha + 0.2f)
        surface.drawTexture(Texture.BLANK, -middleLineSize, yStart.toFloat(), middleLineSize, (yEnd - yStart).toFloat())
        surface.drawTexture(Texture.BLANK, xStart.toFloat(), -middleLineSize, (xEnd - xStart).toFloat(), middleLineSize)
    }

    ////////////////////////////// UTILS //////////////////////////////

    private fun Spatial.isInside(xWorld: Float, yWorld: Float): Boolean
    {
        val padding = getGizmoPadding()
        val w = abs(width) + padding * 2f
        val h = abs(height) + padding * 2f
        val xDiff = xWorld - x
        val yDiff = yWorld - y
        val angle = -MathUtil.atan2(yDiff, xDiff) - (this.rotation / 180f * PI.toFloat())
        val len = sqrt(xDiff * xDiff + yDiff * yDiff)
        val xWorldNew = x + cos(angle) * len
        val yWorldNew = y + sin(angle) * len

        return xWorldNew > x - w / 2f && xWorldNew < x + w / 2 && yWorldNew > y - h / 2f && yWorldNew < y + h / 2f
    }

    private fun Spatial.isOverlapping(xWorld: Float, yWorld: Float, width: Float, height: Float): Boolean
    {
        return this.x > xWorld && this.x < xWorld + width && this.y > yWorld && this.y < yWorld + height
    }

    private fun SceneEntity.createCopy(): SceneEntity
    {
        val entityCopy = this::class.constructors.first().call()
        for (prop in this::class.members)
        {
            if (prop is KMutableProperty<*> && prop.visibility == KVisibility.PUBLIC)
            {
                prop.getter.call(this)?.also { propValue: Any ->
                    if (propValue::class.isData)
                    {
                        // Use .copy() if parameter is a data class
                        val copyFunc = propValue::class.memberFunctions.first { it.name == "copy" }
                        val instanceParam = copyFunc.instanceParameter!!
                        copyFunc.callBy(mapOf(instanceParam to propValue))?.let {
                            entityCopy.setPrimitiveProperty(prop.name, it)
                        }
                    }
                    else if (prop.isPrimitiveValue())
                    {
                        entityCopy.setPrimitiveProperty(prop.name, propValue)
                    }
                    else if (prop.isPrimitiveArray())
                    {
                        entityCopy.setArrayProperty(prop.name, propValue)
                    }
                }
            }
        }
        return entityCopy
    }

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

    private fun removeEntityFromSelection(entity: SceneEntity)
    {
        entitySelection.remove(entity)
        entity.setNot(SELECTED)
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

    private fun resetUI()
    {
        isMoving = false
        isSelecting = false
        isCopying = false
        isRotating = false
        isResizingVertically = false
        isResizingHorizontally = false
        clearEntitySelection()
    }

    override fun onDestroy(engine: PulseEngine)
    {
        if (shouldPersistEditorLayout)
            dockingUI.saveLayout(engine, "/editor_layout.cfg")
    }

    private fun getGizmoPadding() = GIZMO_PADDING * UI_SCALE

    companion object
    {
        const val GIZMO_PADDING = 3
    }
}

data class CameraState(
    val pos: Vector3f,
    val rot: Vector3f,
    val scale: Vector3f
) {
    fun saveFrom(camera: Camera)
    {
        pos.set(camera.position)
        rot.set(camera.rotation)
        scale.set(camera.scale)
    }

    fun loadInto(camera: Camera)
    {
        camera.position.set(pos)
        camera.rotation.set(rot)
        camera.scale.set(scale)
    }

    fun reset()
    {
        pos.set(0f)
        rot.set(0f)
        scale.set(1f)
    }

    companion object
    {
        fun from(camera: Camera) = CameraState(Vector3f(camera.position), Vector3f(camera.rotation), Vector3f(camera.scale))
    }
}