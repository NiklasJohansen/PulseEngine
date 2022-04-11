package no.njoh.pulseengine.widgets.editor

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.CursorType.*
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.console.CommandResult
import no.njoh.pulseengine.core.graphics.Camera
import no.njoh.pulseengine.core.graphics.api.Attachment
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.gui.UiUtil.findElementById
import no.njoh.pulseengine.modules.gui.elements.InputField
import no.njoh.pulseengine.modules.gui.UiElement
import no.njoh.pulseengine.modules.gui.layout.RowPanel
import no.njoh.pulseengine.modules.gui.layout.VerticalPanel
import no.njoh.pulseengine.modules.gui.layout.docking.DockingPanel
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.input.Mouse
import no.njoh.pulseengine.core.scene.Scene
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.REGISTERED_TYPES
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SELECTED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.core.shared.annotations.Property
import no.njoh.pulseengine.modules.physics.PhysicsEntity
import no.njoh.pulseengine.modules.physics.bodies.PhysicsBody
import no.njoh.pulseengine.core.shared.utils.*
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.widget.Widget
import no.njoh.pulseengine.widgets.editor.EditorUtil.MenuBarButton
import no.njoh.pulseengine.widgets.editor.EditorUtil.MenuBarItem
import no.njoh.pulseengine.widgets.editor.EditorUtil.createMenuBarUI
import no.njoh.pulseengine.widgets.editor.EditorUtil.insertSceneSystemProperties
import no.njoh.pulseengine.widgets.editor.EditorUtil.isPrimitiveValue
import org.joml.Vector3f
import kotlin.math.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

class SceneEditor: Widget
{
    override var isRunning = false

    // UI
    private lateinit var rootUI: VerticalPanel
    private lateinit var entityPropertiesUI: RowPanel
    private lateinit var systemPropertiesUI: RowPanel
    private lateinit var dockingUI: DockingPanel
    private lateinit var screenArea: FocusArea
    private lateinit var dragAndDropArea: FocusArea
    private var entityPropertyUiRows = mutableMapOf<String, UiElement>()
    private var showGrid = true

    // Camera
    private val cameraController = Camera2DController(Mouse.MIDDLE)
    private lateinit var activeCamera: Camera
    private lateinit var storedCameraState: CameraState

    // Scene
    private var lastSceneHashCode = -1
    private val entitySelection = mutableListOf<SceneEntity>()
    private var dragAndDropEntity: SceneEntity? = null
    private var changeToType: KClass<out SceneEntity>? = null
    private var sceneFileToLoad: String? = null
    private var sceneFileToCreate: String? = null
    private var sceneFileToSaveAs: String? = null

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

        // Set editor data
        screenArea = FocusArea(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
        dragAndDropArea = FocusArea(0f, 0f, 0f, 0f)
        activeCamera = engine.gfx.mainCamera
        storedCameraState = CameraState.from(activeCamera)
        shouldPersistEditorLayout = engine.config.getBool("persistEditorLayout") ?: false
        isRunning = engine.config.getBool("openEditorOnStart") ?: false
        lastSaveLoadDirectory = engine.data.saveDirectory

        // Create separate render surface for editor UI
        engine.gfx.createSurface("scene_editor_foreground", zOrder = -90, attachments = listOf(Attachment.COLOR_TEXTURE_0, Attachment.DEPTH_TEXTURE))

        // Background surface for grid
        engine.gfx.createSurface("scene_editor_background", zOrder = 20, camera = activeCamera)

        // Load editor icons
        engine.asset.loadAllTextures("/pulseengine/icons")

        // Register a console command to toggle editor visibility
        engine.console.registerCommand("showSceneEditor") {
            isRunning = !isRunning
            if (!isRunning) play(engine) else stop(engine)
            CommandResult("", showCommand = false)
        }

        // Create and populate editor with UI
        createSceneEditorUI(engine)
    }

    private fun createSceneEditorUI(engine: PulseEngine)
    {
        // Properties
        entityPropertiesUI = RowPanel()
        entityPropertiesUI.rowHeight = 34f
        entityPropertiesUI.rowPadding = 0f

        systemPropertiesUI = RowPanel()
        systemPropertiesUI.rowHeight = 34f
        systemPropertiesUI.rowPadding = 0f

        // Create content
        val menuBar = createMenuBarUI(
            MenuBarButton("File", listOf(
                MenuBarItem("New...") { onNewScene(engine) },
                MenuBarItem("Open...") { onLoad(engine) },
                MenuBarItem("Save") { engine.scene.save() },
                MenuBarItem("Save as...") { onSaveAs(engine) }
            )),
            MenuBarButton("View", listOf(
                MenuBarItem("Entity properties") { createEntityPropertyWindow() },
                MenuBarItem("Scene assets") { createAssetWindow(engine) },
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

        // Create root UI and perform initial update
        rootUI = VerticalPanel()
        rootUI.focusable = false
        rootUI.addChildren(menuBar, dockingUI)
        rootUI.updateLayout()
        rootUI.setLayoutClean()

        // Create default windows and insert into docking
        createEntityPropertyWindow()
        createSceneSystemsPropertyWindow(engine)
        createAssetWindow(engine)

        // Load previous layout from file
        if (shouldPersistEditorLayout)
            dockingUI.loadLayout(engine, "/editor_layout.cfg")
    }

    private fun createEntityPropertyWindow()
    {
        if (dockingUI.findElementById("Entity Properties") == null)
        {
            val entityPropertyWindow = EditorUtil.createWindowUI("Entity Properties")
            val propertyPanel = EditorUtil.createScrollableSectionUI(entityPropertiesUI)
            entityPropertyWindow.body.addChildren(propertyPanel)
            dockingUI.insertLeft(entityPropertyWindow)
        }
    }

    private fun createAssetWindow(engine: PulseEngine)
    {
        if (dockingUI.findElementById("Scene Assets") == null)
        {
            val assetWindow = EditorUtil.createWindowUI("Scene Assets")
            val assetPanel = EditorUtil.createAssetPanelUI(engine) { createDragAndDropEntity(engine, it) }
            assetWindow.body.addChildren(assetPanel)
            dockingUI.insertBottom(assetWindow)
        }
    }

    private fun createSceneSystemsPropertyWindow(engine: PulseEngine)
    {
        if (dockingUI.findElementById("Scene Systems") == null)
        {
            val sceneSystemPropertiesUi = EditorUtil.createSystemPropertiesPanelUI(engine, systemPropertiesUI)
            val sceneSystemWindow = EditorUtil.createWindowUI("Scene Systems")
            sceneSystemWindow.body.addChildren(sceneSystemPropertiesUi)
            dockingUI.insertRight(sceneSystemWindow)
        }
    }

    private fun createViewportWindow(engine: PulseEngine)
    {
        val viewportUi = EditorUtil.createViewportUI(engine)
        val viewportWindow = EditorUtil.createWindowUI("Viewport", 300f, 300f, 640f, 480f)
        viewportWindow.body.addChildren(viewportUi)
        dockingUI.addChildren(viewportWindow)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        sceneFileToLoad?.let {
            engine.scene.loadAndSetActive(it)
            sceneFileToLoad = null
        }

        sceneFileToCreate?.let {
            engine.scene.createEmptyAndSetActive(it)
            engine.scene.save()
            sceneFileToCreate = null
        }

        sceneFileToSaveAs?.let {
            engine.scene.saveAs(fileName = it, updateActiveScene = true)
            setWindowTitleFromSceneName(engine)
            sceneFileToSaveAs = null
        }

        if (engine.scene.state == SceneState.STOPPED)
        {
            screenArea.update(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
            engine.input.requestFocus(screenArea)
        }

        if (engine.scene.activeScene.hashCode() != lastSceneHashCode)
        {
            resetUI()
            systemPropertiesUI.insertSceneSystemProperties(engine)
            setWindowTitleFromSceneName(engine)
            initializeEntities(engine)
            lastSceneHashCode = engine.scene.activeScene.hashCode()
        }

        if (engine.scene.state == SceneState.STOPPED)
        {
            engine.input.setCursor(ARROW)
            cameraController.update(engine, activeCamera)
        }

        if (engine.input.wasClicked(Key.F10))
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

        changeToType?.let {
            handleEntityTypeChanged(engine.scene.activeScene, it)
            changeToType = null
        }

        if (entitySelection.size == 1)
            entitySelection.first().handleEntityTransformation(engine)

        handleEntityDeleting(engine)
        handleEntityCopying(engine)
        handleEntitySelection(engine)
        dragAndDropEntity?.handleEntityDragAndDrop(engine)

        if (isMoving)
        {
            engine.input.setCursor(MOVE)
            entitySelection.forEachFast { it.handleEntityMoving(engine) }

            if (!isMoving)
                entitySelection.forEachFast { it.onMovedScaledOrRotated(engine) }
        }

        if (rootUI.width.value.toInt() != engine.window.width || rootUI.height.value.toInt() != engine.window.height)
            rootUI.setLayoutDirty()

        rootUI.update(engine)
    }

    override fun onRender(engine: PulseEngine)
    {
        if (showGrid)
            renderGrid(engine.gfx.getSurfaceOrDefault("scene_editor_background"))

        val foregroundSurface = engine.gfx.getSurfaceOrDefault("scene_editor_foreground")
        renderEntityIconAndGizmo(foregroundSurface, engine)
        renderSelectionRectangle(foregroundSurface)
        rootUI.render(foregroundSurface)
    }

    private fun renderEntityIconAndGizmo(surface: Surface2D, engine: PulseEngine)
    {
        val showResizeDots = (entitySelection.size == 1)
        entitySelection.forEachFast { it.renderGizmo(surface, showResizeDots) }

        engine.scene.forEachEntityTypeList { entities ->
            entities[0]::class.findAnnotation<EditorIcon>()?.let { annotation ->
                engine.asset.getSafe<Texture>(annotation.textureAssetName)?.let { texture ->
                    val width = annotation.width
                    val height = annotation.height
                    surface.setDrawColor(1f, 1f, 1f)
                    entities.forEachFast { entity ->
                        val pos = engine.gfx.mainCamera.worldPosToScreenPos(entity.x, entity.y)
                        surface.drawTexture(texture, pos.x, pos.y, width, height, 0f, 0.5f, 0.5f)
                    }
                }
            }
        }
    }

    private fun onSaveAs(engine: PulseEngine)
    {
        if (engine.scene.state == SceneState.RUNNING)
            engine.scene.stop()

        GlobalScope.launch {
            FileChooser.showSaveFileDialog("scn", engine.data.saveDirectory) { filePath ->
                sceneFileToSaveAs = filePath + if (!filePath.endsWith(".scn")) ".scn" else ""
            }
        }
    }

    private fun onLoad(engine: PulseEngine)
    {
        if (engine.scene.state != SceneState.RUNNING)
            engine.scene.save()

        GlobalScope.launch {
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

        GlobalScope.launch {
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
        engine.input.setCursor(ARROW)

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
            initializeEntities(engine)
        }
    }

    ////////////////////////////// EDIT TOOLS  //////////////////////////////

    private fun handleEntityDeleting(engine: PulseEngine)
    {
        if (engine.input.isPressed(Key.DELETE))
        {
            if (entitySelection.isNotEmpty())
            {
                entitySelection.forEachFast { it.set(DEAD) }
                clearEntitySelection()
                isMoving = false
            }
        }
    }

    private fun handleEntityCopying(engine: PulseEngine)
    {
        if (engine.input.isPressed(Key.LEFT_CONTROL))
        {
            if (isMoving && !isCopying)
            {
                val copies = entitySelection.map { it.createCopy() }

                if (copies.size == 1)
                    selectSingleEntity(copies.first())
                else
                {
                    clearEntitySelection()
                    copies.forEachFast { addEntityToSelection(it) }
                }
                val scene = engine.scene.activeScene
                copies.forEachFast { scene.insertEntity(it) }
                isCopying = true
            }
        } else isCopying = false
    }

    private fun handleEntitySelection(engine: PulseEngine)
    {
        val xMouse = engine.input.xWorldMouse
        val yMouse = engine.input.yWorldMouse

        if (engine.input.wasClicked(Mouse.LEFT))
        {
            // Select entity
            if (!isMoving && !isSelecting && !isRotating && !isResizingVertically && !isResizingHorizontally)
            {
                var zMin = Float.MAX_VALUE
                var closestEntity: SceneEntity? = null
                engine.scene.forEachEntity()
                {
                    if (it.z <= zMin && it.isInside(xMouse, yMouse))
                    {
                        zMin = it.z
                        closestEntity = it
                    }
                }

                closestEntity?.let { entity ->
                    if (entity !in entitySelection)
                        selectSingleEntity(entity)
                    isMoving = true
                }
            }
        }

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

        if (isSelecting)
        {
            val xStart = min(xStartSelect, xEndSelect)
            val yStart = min(yStartSelect, yEndSelect)
            val width  = abs(xEndSelect - xStartSelect)
            val height  = abs(yEndSelect - yStartSelect)
            val selectedEntity = entitySelection.firstOrNull()
            entitySelection.forEachFast { it.setNot(SELECTED) }
            entitySelection.clear()

            engine.scene.forEachEntity()
            {
                if (it.isOverlapping(xStart, yStart, width, height))
                    addEntityToSelection(it)
            }

            if (entitySelection.size == 1)
            {
                if (entitySelection.first() !== selectedEntity)
                    selectSingleEntity(entitySelection.first())
            }
            else entityPropertiesUI.clearChildren()
        }

        var xMove = 0f
        var yMove = 0f
        if (engine.input.wasClicked(Key.UP)) yMove -= 1
        if (engine.input.wasClicked(Key.DOWN)) yMove += 1
        if (engine.input.wasClicked(Key.LEFT)) xMove -= 1
        if (engine.input.wasClicked(Key.RIGHT)) xMove += 1
        if (xMove != 0f || yMouse != 0f)
        {
            entitySelection.forEachFast {
                it.x += xMove
                it.y += yMove
                it.set(POSITION_UPDATED)
            }
        }
    }

    private fun SceneEntity.handleEntityMoving(engine: PulseEngine)
    {
        if (isMoving && !engine.input.isPressed(Mouse.LEFT))
        {
            engine.input.setCursor(ARROW)
            isMoving = false
            this.onMovedScaledOrRotated(engine)
        }

        this.x += engine.input.xdMouse / activeCamera.scale.x
        this.y += engine.input.ydMouse / activeCamera.scale.y
        this.set(POSITION_UPDATED)

        updatePropertiesPanel(::x.name, x)
        updatePropertiesPanel(::y.name, y)
    }

    private fun SceneEntity.handleEntityDragAndDrop(engine: PulseEngine)
    {
        if (this !in entitySelection)
            selectSingleEntity(this)

        engine.input.acquireFocus(dragAndDropArea)
        this.x = engine.input.xWorldMouse
        this.y = engine.input.yWorldMouse

        if (!engine.input.isPressed(Mouse.LEFT))
        {
            dragAndDropEntity = null
            engine.scene.activeScene.insertEntity(this)
            updatePropertiesPanel("id", this.id)
            this.onMovedScaledOrRotated(engine)
        }
    }

    private fun SceneEntity.handleEntityTransformation(engine: PulseEngine)
    {
        val border = min(abs(width), abs(height)) * 0.1f
        val rotateArea = min(abs(width), abs(height)) * 0.2f

        val xDiff = engine.input.xWorldMouse - x
        val yDiff = engine.input.yWorldMouse - y
        val mouseEntityAngle = -atan2(yDiff, xDiff)
        val angle = mouseEntityAngle - (this.rotation / 180f * PI.toFloat())
        val len = sqrt(xDiff * xDiff + yDiff * yDiff)
        val xMouse = x + cos(angle) * len
        val yMouse = y + sin(angle) * len
        val w = (abs(width) + GIZMO_PADDING * 2) / 2
        val h = (abs(height) + GIZMO_PADDING * 2) / 2

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

        cursorType?.let { engine.input.setCursor(it) }

        if (isRotating || isResizingHorizontally || isResizingVertically)
        {
            updatePropertiesPanel(::rotation.name, rotation)
            updatePropertiesPanel(::width.name, width)
            updatePropertiesPanel(::height.name, height)
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

    private fun createDragAndDropEntity(engine: PulseEngine, texture: Texture)
    {
        if (dragAndDropEntity != null) return

        val type = REGISTERED_TYPES
            .find { it.memberProperties.any { prop -> prop.name == "textureName" } }
            ?: REGISTERED_TYPES.firstOrNull()

        type?.let { type ->
            val entity = type.constructors.first().call()
            entity.x = engine.input.xWorldMouse
            entity.y = engine.input.yWorldMouse
            entity.width = texture.width.toFloat()
            entity.height = texture.height.toFloat()
            EditorUtil.setProperty(entity, "textureName", texture.name)
            dragAndDropEntity = entity
        }
    }

    private fun selectSingleEntity(entity: SceneEntity)
    {
        clearEntitySelection()
        addEntityToSelection(entity)

        val entityTypePropUI = EditorUtil.createEntityTypePropertyUI(entity) { changeToType = it }
        entityPropertiesUI.addChildren(entityTypePropUI)

        entity::class.memberProperties
            .groupBy { it.findAnnotation<Property>()?.category ?: "" }
            .toList()
            .sortedBy { it.first }
            .forEachFast { (category, props) ->
                props.sortedBy { it.findAnnotation<Property>()?.order ?: 0 }
                    .filterIsInstance<KMutableProperty<*>>()
                    .filter { EditorUtil.isPropertyEditable(it) }
                    .also {
                        if (it.isNotEmpty() && category.isNotEmpty())
                            entityPropertiesUI.addChildren(EditorUtil.createCategoryHeader(category))
                    }.forEachFast { prop ->
                        val (propertyPanel, inputElement) = EditorUtil.createPropertyUI(entity, prop)
                        entityPropertiesUI.addChildren(propertyPanel)
                        entityPropertyUiRows[prop.name] = inputElement
                    }
            }
    }

    private fun handleEntityTypeChanged(scene: Scene, type: KClass<out SceneEntity>)
    {
        if (entitySelection.size != 1) return

        try
        {
            val oldEntity = entitySelection.first()
            val newEntity = type.constructors.first().call()

            oldEntity::class.memberProperties.forEach { prop ->
                if (prop.visibility == KVisibility.PUBLIC)
                    prop.getter.call(oldEntity)?.let { value -> EditorUtil.setProperty(newEntity, prop.name, value) }
            }

            oldEntity.set(DEAD)
            scene.insertEntity(newEntity)
            selectSingleEntity(newEntity)
        }
        catch (e: Exception) { Logger.error("Failed to change entity type, reason: ${e.message}") }
    }

    private fun updatePropertiesPanel(propName: String, value: Any)
    {
        entityPropertyUiRows[propName]?.let {
            if (it is InputField)
                it.text = value.toString()
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
        val pos = activeCamera.worldPosToScreenPos(x, y)
        val w = (width + GIZMO_PADDING * 2) * activeCamera.scale.x / 2f
        val h = (height + GIZMO_PADDING * 2) * activeCamera.scale.y / 2f
        val size = 4f
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
        val color = (activeCamera.scale.x + 0.2f).coerceIn(0.1f, 0.4f)

        surface.setDrawColor(1f, 1f, 1f, color + 0.1f)
        for (x in xStart until xEnd step cellSize)
            if (x != 0 && x % 3 == 0) surface.drawLine(x.toFloat(), yStart.toFloat(), x.toFloat(), yEnd.toFloat())

        for (y in yStart until yEnd step cellSize)
            if (y != 0 && y % 3 == 0) surface.drawLine(xStart.toFloat(), y.toFloat(), xEnd.toFloat(), y.toFloat())

        surface.setDrawColor(1f, 1f, 1f, color)
        for (x in xStart until xEnd step cellSize)
            if (x != 0 && x % 3 != 0) surface.drawLine(x.toFloat(), yStart.toFloat(), x.toFloat(), yEnd.toFloat())

        for (y in yStart until yEnd step cellSize)
            if (y != 0 && y % 3 != 0) surface.drawLine(xStart.toFloat(), y.toFloat(), xEnd.toFloat(), y.toFloat())

        surface.setDrawColor(1f, 1f, 1f, color + 0.2f)
        surface.drawTexture(Texture.BLANK, -middleLineSize, yStart.toFloat(), middleLineSize, (yEnd - yStart).toFloat())
        surface.drawTexture(Texture.BLANK, xStart.toFloat(), -middleLineSize, (xEnd - xStart).toFloat(), middleLineSize)
    }

    ////////////////////////////// UTILS //////////////////////////////

    private fun SceneEntity.isInside(xWorld: Float, yWorld: Float): Boolean
    {
        val w = abs(width) + GIZMO_PADDING * 2
        val h = abs(height) + GIZMO_PADDING * 2
        val xDiff = xWorld - x
        val yDiff = yWorld - y
        val angle = -MathUtil.atan2(yDiff, xDiff) - (this.rotation / 180f * PI.toFloat())
        val len = sqrt(xDiff * xDiff + yDiff * yDiff)
        val xWorldNew = x + cos(angle) * len
        val yWorldNew = y + sin(angle) * len

        return xWorldNew > x - w / 2f && xWorldNew < x + w / 2 && yWorldNew > y - h / 2f && yWorldNew < y + h / 2f
    }

    private fun SceneEntity.isOverlapping(xWorld: Float, yWorld: Float, width: Float, height: Float): Boolean
    {
        return this.x > xWorld && this.x < xWorld + width && this.y > yWorld && this.y < yWorld + height
    }

    private fun SceneEntity.createCopy(): SceneEntity
    {
        val copy = this::class.constructors.first().call()
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
                            EditorUtil.setProperty(copy, prop.name, it)
                        }
                    }
                    else if (prop.isPrimitiveValue())
                    {
                        EditorUtil.setProperty(copy, prop.name, propValue)
                    }
                }
            }
        }
        return copy
    }

    private fun clearEntitySelection()
    {
        entitySelection.forEachFast { it.setNot(SELECTED) }
        entitySelection.clear()
        entityPropertiesUI.clearChildren()
        entityPropertyUiRows.clear()
    }

    private fun addEntityToSelection(entity: SceneEntity)
    {
        entitySelection.add(entity)
        entity.set(SELECTED)
    }

    private fun initializeEntities(engine: PulseEngine)
    {
        engine.scene.forEachEntity {
            if (prevSelectedEntityId != null && prevSelectedEntityId == it.id)
                selectSingleEntity(it)
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
        dragAndDropEntity = null
        changeToType = null
        isMoving = false
        isSelecting = false
        isCopying = false
        isRotating = false
        isResizingVertically = false
        isResizingHorizontally = false
        clearEntitySelection()
    }

    private fun setWindowTitleFromSceneName(engine: PulseEngine)
    {
        engine.window.title = engine.window.title
            .substringBeforeLast(" [")
            .plus(" [${engine.scene.activeScene.fileName.removePrefix("/")}]")
    }

    override fun onDestroy(engine: PulseEngine)
    {
        if (shouldPersistEditorLayout)
            dockingUI.saveLayout(engine, "/editor_layout.cfg")
    }

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