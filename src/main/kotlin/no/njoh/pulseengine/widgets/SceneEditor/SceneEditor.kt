package no.njoh.pulseengine.widgets.SceneEditor

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.*
import no.njoh.pulseengine.data.CursorType.*
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.console.CommandResult
import no.njoh.pulseengine.modules.graphics.CameraInterface
import no.njoh.pulseengine.modules.graphics.Surface2D

import no.njoh.pulseengine.modules.graphics.ui.elements.*
import no.njoh.pulseengine.modules.graphics.ui.layout.*
import no.njoh.pulseengine.modules.graphics.ui.layout.docking.DockingPanel
import no.njoh.pulseengine.modules.scene.Scene
import no.njoh.pulseengine.modules.scene.SceneEntity
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.util.Camera2DController
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.modules.scene.SpatialIndex
import no.njoh.pulseengine.widgets.SceneEditor.EditorUtil.createMenuBarUI
import no.njoh.pulseengine.widgets.Widget
import kotlin.math.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class SceneEditor: Widget
{
    // UI
    private lateinit var rootUI: VerticalPanel
    private lateinit var propertiesUI: RowPanel
    private lateinit var dockingUI: DockingPanel
    private lateinit var screenArea: FocusArea
    private lateinit var dragAndDropArea: FocusArea
    private var propertyUiRows = mutableMapOf<String, UiElement>()
    private var isVisible = true

    // Camera
    private val cameraController = Camera2DController(Mouse.MIDDLE)
    private lateinit var activeCamera: CameraInterface

    // Scene
    private var scene: Scene? = null
    private val entitySelection = mutableListOf<SceneEntity>()
    private var dragAndDropEntity: SceneEntity? = null
    private var changeToType: KClass<out SceneEntity>? = null

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

    // Loading and saving
    private var shouldPersistEditorLayout = false

    override fun onCreate(engine: PulseEngine)
    {
        screenArea = FocusArea(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
        dragAndDropArea = FocusArea(0f, 0f, 0f, 0f)
        activeCamera = engine.gfx.mainCamera

        engine.config.load("/pulseengine/config/editor_config.cfg")
        shouldPersistEditorLayout = engine.config.getBool("persistEditorLayout") ?: false
        isVisible = engine.config.getBool("openEditorOnStart") ?: false

        engine.gfx.createSurface2D("sceneEditorSurface")
        engine.console.registerCommand("showSceneEditor") {
            isVisible = !isVisible
            if (!isVisible)
            {
                setScene(null)
                engine.input.setCursor(ARROW)
                if (engine.scene.sceneState == SceneState.STOPPED) {
                    engine.scene.save()
                    engine.scene.start()
                }
            }
            else
            {
                engine.scene.stop()
                engine.scene.reload()
            }

            CommandResult("", showCommand = false)
        }

        createSceneEditorUI(engine)

        if (shouldPersistEditorLayout)
            dockingUI.loadLayout(engine, "/editor_layout.cfg")
    }

    private fun createSceneEditorUI(engine: PulseEngine)
    {
        // Properties
        propertiesUI = RowPanel()
        propertiesUI.rowHeight = 50f
        propertiesUI.rowPadding = 0f

        // Create windows
        val scenePropertyWindows = EditorUtil.createWindowUI("Scene Properties")
        val assetWindow = EditorUtil.createWindowUI("Scene Assets")
        val entityPropertyWindow = EditorUtil.createWindowUI("Entity Properties")

        // Create content
        val menuBar = createMenuBarUI()
        val assetPanel = EditorUtil.createAssetPanelUI(engine) { createDragAndDropEntity(engine, it) }
        val propertyPanel = EditorUtil.createScrollableSectionUI(propertiesUI)

        // Add content to windows
        assetWindow.body.addChildren(assetPanel)
        entityPropertyWindow.body.addChildren(propertyPanel)

        // Panel for docking of windows
        dockingUI = DockingPanel()
        dockingUI.focusable = false

        // Create root UI and perform initial update
        rootUI = VerticalPanel()
        rootUI.focusable = false
        rootUI.addChildren(menuBar, dockingUI)
        rootUI.update(engine)

        // Insert windows into docking
        dockingUI.insertLeft(entityPropertyWindow)
        dockingUI.insertRight(scenePropertyWindows)
        dockingUI.insertBottom(assetWindow)
    }

    override fun onUpdate(engine: PulseEngine)
    {
         if (!isVisible) return

        if (engine.scene.sceneState == SceneState.STOPPED)
        {
            screenArea.update(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
            engine.input.requestFocus(screenArea)
        }

        // Set default cursor
        engine.input.setCursor(ARROW)

        cameraController.update(engine, activeCamera)

        if (engine.scene.activeScene != scene)
            engine.scene.activeScene?.let { setScene(it) }

        if (engine.input.wasClicked(Key.P))
            rootUI.printStructure()

        if (engine.input.wasClicked(Key.G))
            SpatialIndex.draw = !SpatialIndex.draw

        changeToType?.let {
            handleEntityTypeChanged(it)
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
            entitySelection.forEach { it.handleEntityMoving(engine) }
        }

        rootUI.update(engine)
    }

    override fun onRender(engine: PulseEngine)
    {
        if (!isVisible) return

        val uiSurface = engine.gfx.getSurface2D("sceneEditorSurface")
        val showResizeDots = (entitySelection.size == 1)
        entitySelection.forEach { it.renderGizmo(uiSurface, showResizeDots) }
        renderSelectionRectangle(uiSurface)
        rootUI.render(uiSurface)
    }

    ////////////////////////////// EDIT TOOLS  //////////////////////////////

    private fun handleEntityDeleting(engine: PulseEngine)
    {
        if (engine.input.isPressed(Key.DELETE))
        {
            if (entitySelection.isNotEmpty())
            {
                entitySelection.forEach { it.set(DEAD) }
                entitySelection.clear()
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
                    entitySelection.clear()
                    propertyUiRows.clear()
                    propertiesUI.clearChildren()
                    entitySelection.addAll(copies)
                }

                copies.forEach { scene?.addEntity(it) }
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
                scene?.let { scene ->

                    scene.entityTypes.forEach { (type, entities) ->

                        for (i in entities.size - 1 downTo 0)
                        {
                            val entity = entities[i]
                            if (entity.isInside(xMouse, yMouse))
                            {
                                if (entity !in entitySelection)
                                {
                                    entitySelection.clear()
                                    selectSingleEntity(entity)
                                }

                                isMoving = true
                                break
                            }
                        }
                    }
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
            scene?.let {

                val xStart = min(xStartSelect, xEndSelect)
                val yStart = min(yStartSelect, yEndSelect)
                val width  = abs(xEndSelect - xStartSelect)
                val height  = abs(yEndSelect - yStartSelect)
                val selectedEntity = entitySelection.firstOrNull()

                entitySelection.clear()

                it.entityTypes.forEach { (type, entities) ->
                    for (entity in entities)
                    {
                        if (entity.isOverlapping(xStart, yStart, width, height))
                            entitySelection.add(entity)
                    }
                }

                if (entitySelection.size == 1)
                {
                    if (entitySelection.first() != selectedEntity)
                        selectSingleEntity(entitySelection.first())
                }
                else propertiesUI.clearChildren()
            }
        }

        var xMove = 0f
        var yMove = 0f
        if (engine.input.wasClicked(Key.UP)) yMove -= 1
        if (engine.input.wasClicked(Key.DOWN)) yMove += 1
        if (engine.input.wasClicked(Key.LEFT)) xMove -= 1
        if (engine.input.wasClicked(Key.RIGHT)) xMove += 1
        if (xMove != 0f || yMouse != 0f)
        {
            entitySelection.forEach {
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
        }

        this.x += engine.input.xdMouse / activeCamera.xScale
        this.y += engine.input.ydMouse / activeCamera.yScale
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
            scene?.addEntity(this)
        }
    }

    private fun SceneEntity.handleEntityTransformation(engine: PulseEngine)
    {
        val border = min(abs(width), abs(height)) * 0.1f
        val rotateArea = min(abs(width), abs(height)) * 0.2f

        val xDiff = engine.input.xWorldMouse - x
        val yDiff = engine.input.yWorldMouse - y
        val mouseEntityAngle = atan2(yDiff, xDiff)
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
        //engine.input.setCursor(cursorType)

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
        var iconAngle = startAngle + when
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
        if (dragAndDropEntity == null) return

        SceneEntity.REGISTERED_TYPES
            .find { it.memberProperties.any { it.name == "textureName" } }
            ?: SceneEntity.REGISTERED_TYPES.firstOrNull()
            ?.let { type ->
                val entity = type.constructors.first().call()
                entity.x = engine.input.xWorldMouse
                entity.y = engine.input.yWorldMouse
                entity.width = texture.width.toFloat()
                entity.height = texture.height.toFloat()
                EditorUtil.setEntityProperty(entity, "textureName", texture.name)
                dragAndDropEntity = entity
            }
    }

    private fun selectSingleEntity(entity: SceneEntity)
    {
        entitySelection.clear()
        entitySelection.add(entity)
        propertiesUI.clearChildren()
        propertyUiRows.clear()

        val entityTypePropUI = EditorUtil.createEntityTypePropertyUI(entity) { changeToType = it }
        propertiesUI.addChildren(entityTypePropUI)

        for (prop in entity::class.memberProperties)
        {
            if (prop !is KMutableProperty<*> || prop.visibility == KVisibility.PRIVATE)
                continue

            val (propertyPanel, inputElement) = EditorUtil.createEntityPropertyUI(entity, prop)
            propertiesUI.addChildren(propertyPanel)
            propertyUiRows[prop.name] = inputElement
        }
    }

    private fun handleEntityTypeChanged(type: KClass<out SceneEntity>)
    {
        if (entitySelection.size != 1) return

        try
        {
            val oldEntity = entitySelection.first()
            val newEntity = type.constructors.first().call()

            oldEntity::class.memberProperties.forEach { prop ->
                if (prop.visibility == KVisibility.PUBLIC)
                    prop.getter.call(oldEntity)?.let { value -> EditorUtil.setEntityProperty(newEntity, prop.name, value) }
            }

            oldEntity.set(DEAD)
            scene?.addEntity(newEntity)
            selectSingleEntity(newEntity)
        }
        catch (e: Exception) { Logger.error("Failed to change entity type, reason: ${e.message}") }
    }

    private fun updatePropertiesPanel(propName: String, value: Any)
    {
        propertyUiRows[propName]?.let {
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
            val w = (xEndSelect - xStartSelect) * activeCamera.xScale
            val h = (yEndSelect - yStartSelect) * activeCamera.yScale

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
        val w = (width + GIZMO_PADDING * 2) * activeCamera.xScale / 2f
        val h = (height + GIZMO_PADDING * 2) * activeCamera.yScale / 2f
        val size = 4f
        val halfSize = size / 2f

        if (rotation != 0f)
        {
            val r = this.rotation / 180f * PI.toFloat()
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

    ////////////////////////////// SceneEntity UTILS  //////////////////////////////

    private fun SceneEntity.isInside(xWorld: Float, yWorld: Float): Boolean
    {
        val w = (abs(width) + GIZMO_PADDING * 2)
        val h = (abs(height) + GIZMO_PADDING * 2)
        val xDiff = xWorld - x
        val yDiff = yWorld - y
        val angle = atan2(yDiff, xDiff) - (this.rotation / 180f * PI.toFloat())
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
                prop.getter.call(this)?.let { EditorUtil.setEntityProperty(copy, prop.name, it) }
        }
        return copy
    }

    private fun setScene(scene: Scene?)
    {
        Logger.debug("Setting scene")
        this.scene = scene
        dragAndDropEntity = null
        changeToType = null
        isMoving = false
        isSelecting = false
        isCopying = false
        isRotating = false
        isResizingVertically = false
        isResizingHorizontally = false
        propertiesUI.clearChildren()
        propertyUiRows.clear()
        entitySelection.clear()
    }

    override fun onDestroy(engine: PulseEngine) {
        if (shouldPersistEditorLayout)
            dockingUI.saveLayout(engine, "/editor_layout.cfg")
    }

    companion object
    {
        const val GIZMO_PADDING = 3
    }
}

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValueRange(val min: Float, val max: Float)