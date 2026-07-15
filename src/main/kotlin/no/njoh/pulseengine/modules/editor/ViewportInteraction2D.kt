package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.input.CursorType.*
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.input.Key.*
import no.njoh.pulseengine.core.input.MouseButton
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.EDITABLE
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.core.scene.interfaces.Spatial2D
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Camera2DController
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.MathUtil
import no.njoh.pulseengine.modules.ui.UiParams.UI_SCALE
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.reflect.full.findAnnotation

/** 
 * 2D selection, camera, transformation and rendering for the scene editor viewport. 
 */
class ViewportInteraction2D(
    initialCameraState: CameraState = CameraState.orthographic2D()
) : ViewportInteraction {

    private val cameraController = Camera2DController(MouseButton.MIDDLE, smoothing = 0f)
    private val defaultCameraState = initialCameraState.duplicate()
    private var cameraState = defaultCameraState.duplicate()

    // Movement
    private var isMoving = false

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

    // Selection
    private var isSelecting = false
    private var xStartSelect = 0f
    private var yStartSelect = 0f
    private var xEndSelect = 0f
    private var yEndSelect = 0f

    override fun onCreate(engine: PulseEngine, context: ViewportContext)
    {
        engine.gfx.createSurface(
            name = GRID_SURFACE,
            zOrder = 20,
            camera = context.camera,
            clearColor = Color(0.001f, 0.001f, 0.001f, 1f)
        )
        engine.gfx.createSurface(
            name = GIZMO_SURFACE, 
            zOrder = -50
        )
    }

    override fun onUpdate(engine: PulseEngine, context: ViewportContext)
    {
        engine.input.setCursorType(ARROW)
        cameraController.scrollSpeed = 40f * UI_SCALE
        cameraController.update(engine, context.camera, enableScrolling = engine.input.hasHoverFocus(context.focusArea))

        if (context.selection.size == 1)
            updateEntityTransformation(engine, context, context.selection.first())

        updateSelection(engine, context)
        updateEntityMovement(engine, context)
    }

    override fun onEditorActivated(engine: PulseEngine, context: ViewportContext)
    {
        cameraState.loadInto(context.camera, engine.window.width, engine.window.height)
        reset(engine, context)
    }

    override fun onEditorDeactivated(engine: PulseEngine, context: ViewportContext)
    {
        cameraState.saveFrom(context.camera)
        reset(engine, context)
    }

    override fun resetCamera(engine: PulseEngine, context: ViewportContext)
    {
        cameraState = defaultCameraState.duplicate()
        cameraState.loadInto(context.camera, engine.window.width, engine.window.height)
        reset(engine, context)
    }

    override fun onRender(engine: PulseEngine, context: ViewportContext)
    {
        if (context.isGridVisible)
            engine.gfx.getSurface(GRID_SURFACE)?.let { renderGrid(it, context) }

        engine.gfx.getSurface(GIZMO_SURFACE)?.let() 
        {
            renderEntityGizmos(it, context)
            renderSelectionRectangle(it, context)
        }

        engine.gfx.getSurface(UI_BASE_SURFACE)?.let { renderEntityIcons(it, engine, context) }
    }

    override fun reset(engine: PulseEngine, context: ViewportContext)
    {
        isMoving = false
        isSelecting = false
        isRotating = false
        isResizingVertically = false
        isResizingHorizontally = false
        engine.input.setCursorType(ARROW)
    }

    override fun onDestroy(engine: PulseEngine, context: ViewportContext)
    {
        engine.input.setCursorType(ARROW)
        engine.gfx.deleteSurface(GRID_SURFACE)
        engine.gfx.deleteSurface(GIZMO_SURFACE)
    }

    private fun updateSelection(engine: PulseEngine, context: ViewportContext)
    {
        if (engine.input.wasClicked(A) && engine.input.isPressed(LEFT_CONTROL))
        {
            val entities = mutableListOf<SceneEntity>()
            engine.scene.forEachEntity { entities += it }
            context.selectMultiple(engine, entities)
        }

        val xMouse = engine.input.xWorldMouse
        val yMouse = engine.input.yWorldMouse

        if (engine.input.wasClicked(MouseButton.LEFT) && !isTransforming())
        {
            var zMin = Float.MAX_VALUE
            var closestEntity: SceneEntity? = null
            engine.scene.forEachEntity { entity ->
                if (entity is Spatial2D && entity.z <= zMin && entity.isInside(xMouse, yMouse) && entity.isSet(EDITABLE) && entity.isNot(HIDDEN))
                {
                    zMin = entity.z
                    closestEntity = entity
                }
            }

            closestEntity?.let { entity ->
                val selected = context.selection
                if (engine.input.isPressed(LEFT_CONTROL))
                {
                    val updatedSelection = if (entity in selected) selected - entity else selected + entity
                    context.selectMultiple(engine, updatedSelection)
                }
                else if (entity !in selected)
                {
                    context.selectSingle(engine, entity)
                }
                isMoving = true
            }
        }

        if (engine.input.isPressed(MouseButton.LEFT))
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
        else
        {
            isSelecting = false
        }

        if (isSelecting)
        {
            val xStart = min(xStartSelect, xEndSelect)
            val yStart = min(yStartSelect, yEndSelect)
            val width = abs(xEndSelect - xStartSelect)
            val height = abs(yEndSelect - yStartSelect)
            val selection = if (engine.input.isPressed(LEFT_CONTROL)) context.selection.toMutableList() else mutableListOf()

            engine.scene.forEachEntity { entity ->
                if (entity is Spatial2D && entity.isSet(EDITABLE) && entity.isNot(HIDDEN) &&
                    entity !in selection && entity.isOverlapping(xStart, yStart, width, height))
                {
                    selection += entity
                }
            }
            if (selection != context.selection)
                context.selectMultiple(engine, selection)
        }
    }

    private fun updateEntityMovement(engine: PulseEngine, context: ViewportContext)
    {
        var xMove = 0f
        var yMove = 0f
        if (engine.input.wasClicked(UP)) yMove -= 1f
        if (engine.input.wasClicked(DOWN)) yMove += 1f
        if (engine.input.wasClicked(LEFT)) xMove -= 1f
        if (engine.input.wasClicked(RIGHT)) xMove += 1f
        if (xMove != 0f || yMove != 0f)
        {
            context.selection.forEachFast { entity ->
                if (entity is Spatial2D)
                {
                    entity.x += xMove
                    entity.y += yMove
                    entity.set(POSITION_UPDATED)
                    context.notifyTransformChanged(engine, entity, entity::x.name, entity::y.name)
                }
            }
        }

        if (!isMoving) return

        if (!engine.input.isPressed(MouseButton.LEFT))
        {
            engine.input.setCursorType(ARROW)
            isMoving = false
            return
        }

        val xDelta = engine.input.xdMouse / context.camera.scale.x
        val yDelta = engine.input.ydMouse / context.camera.scale.y
        if (xDelta == 0f && yDelta == 0f) return

        context.selection.forEachFast { entity ->
            if (entity is Spatial2D)
            {
                entity.x += xDelta
                entity.y += yDelta
                entity.set(POSITION_UPDATED)
                context.notifyTransformChanged(engine, entity, entity::x.name, entity::y.name)
            }
        }
    }

    private fun updateEntityTransformation(engine: PulseEngine, context: ViewportContext, entity: SceneEntity)
    {
        if (entity !is Spatial2D) return

        val border = min(abs(entity.width), abs(entity.height)) * 0.1f
        val rotateArea = min(abs(entity.width), abs(entity.height)) * 0.2f
        val xDiff = engine.input.xWorldMouse - entity.x
        val yDiff = engine.input.yWorldMouse - entity.y
        val mouseEntityAngle = -atan2(yDiff, xDiff)
        val angle = mouseEntityAngle - entity.rotation / 180f * PI.toFloat()
        val len = sqrt(xDiff * xDiff + yDiff * yDiff)
        val xMouse = entity.x + cos(angle) * len
        val yMouse = entity.y + sin(angle) * len
        val padding = gizmoPadding()
        val width = (abs(entity.width) + padding * 2f) / 2f
        val height = (abs(entity.height) + padding * 2f) / 2f

        val resizeBottom = xMouse in entity.x - width - border..entity.x + width + border && yMouse in entity.y + height - border..entity.y + height + border
        val resizeTop = xMouse in entity.x - width - border..entity.x + width + border && yMouse in entity.y - height - border..entity.y - height + border
        val resizeLeft = xMouse in entity.x - width - border..entity.x - width + border && yMouse in entity.y - height - border..entity.y + height + border
        val resizeRight = xMouse in entity.x + width - border..entity.x + width + border && yMouse in entity.y - height - border..entity.y + height + border

        val rotateTopLeft = xMouse in entity.x - width - rotateArea..entity.x - width && yMouse in entity.y - height - rotateArea..entity.y - height
        val rotateBottomLeft = xMouse in entity.x - width - rotateArea..entity.x - width && yMouse in entity.y + height..entity.y + height + rotateArea
        val rotateTopRight = xMouse in entity.x + width..entity.x + width + rotateArea && yMouse in entity.y - height - rotateArea..entity.y - height
        val rotateBottomRight = xMouse in entity.x + width..entity.x + width + rotateArea && yMouse in entity.y + height..entity.y + height + rotateArea

        if (engine.input.isPressed(MouseButton.LEFT))
        {
            if (!isTransforming())
            {
                if (resizeBottom || resizeTop)
                {
                    isResizingVertically = true
                    entityStartWidth = entity.width
                    entityStartHeight = entity.height
                    xMouseStart = xMouse
                    yMouseStart = yMouse
                    yResizeDirection = (if (yMouse > entity.y) -1f else 1f) * (if (entity.height < 0f) -1f else 1f)
                    resizeIconAngle = getIconAngle(entity.rotation, resizeLeft, resizeRight, resizeTop, resizeBottom)
                }

                if (resizeLeft || resizeRight)
                {
                    isResizingHorizontally = true
                    entityStartWidth = entity.width
                    entityStartHeight = entity.height
                    xMouseStart = xMouse
                    yMouseStart = yMouse
                    xResizeDirection = (if (xMouse > entity.x) -1f else 1f) * (if (entity.width < 0f) -1f else 1f)
                    resizeIconAngle = getIconAngle(entity.rotation, resizeLeft, resizeRight, resizeTop, resizeBottom)
                }

                if (!isResizingVertically && !isResizingHorizontally &&
                    (rotateTopLeft || rotateBottomLeft || rotateTopRight || rotateBottomRight))
                {
                    isRotating = true
                    entityStartAngle = entity.rotation
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

        val controlPressed = engine.input.isPressed(Key.LEFT_CONTROL)
        val shiftPressed = engine.input.isPressed(Key.LEFT_SHIFT)
        when
        {
            isRotating -> 
            {
                val diff = (mouseEntityAngle - mouseStartAngle) / PI.toFloat() * 180f
                entity.rotation = if (controlPressed) ((entityStartAngle + diff).toInt() / 45 * 45).toFloat() else entityStartAngle + diff
            }
            isResizingHorizontally && shiftPressed -> 
            {
                val delta = xMouseStart - xMouse
                val ratio = entityStartHeight / if (entityStartWidth == 0f) entityStartHeight else entityStartWidth
                entity.width = entityStartWidth + delta * xResizeDirection
                entity.height = entityStartHeight + ratio * delta * xResizeDirection
            }
            isResizingVertically && shiftPressed -> 
            {
                val delta = yMouseStart - yMouse
                val ratio = entityStartWidth / if (entityStartHeight == 0f) entityStartWidth else entityStartHeight
                entity.height = entityStartHeight + delta * yResizeDirection
                entity.width = entityStartWidth + ratio * delta * yResizeDirection
            }
            isResizingHorizontally && isResizingVertically -> 
            {
                entity.width = entityStartWidth + (xMouseStart - xMouse) * xResizeDirection
                entity.height = entityStartHeight + (yMouseStart - yMouse) * yResizeDirection
            }
            isResizingHorizontally ->
            {
                entity.width = entityStartWidth + (xMouseStart - xMouse) * xResizeDirection
            }
            isResizingVertically ->
            {
                entity.height = entityStartHeight + (yMouseStart - yMouse) * yResizeDirection
            }
        }

        if (!isResizingHorizontally && !isResizingVertically)
            resizeIconAngle = getIconAngle(entity.rotation, resizeLeft, resizeRight, resizeTop, resizeBottom)

        val cursorType =
            if (!isRotating && (isResizingHorizontally || isResizingVertically || resizeBottom || resizeTop || resizeLeft || resizeRight))
            {
                when (resizeIconAngle)
                {
                    in 0f..22f, in 158f..202f, in 338f..360f -> HORIZONTAL_RESIZE
                    in 22f..68f, in 202f..248f -> TOP_LEFT_RESIZE
                    in 68f..112f, in 248f..292f -> VERTICAL_RESIZE
                    in 112f..158f, in 292f..338f -> TOP_RIGHT_RESIZE
                    else -> ARROW
                }
            }
            else if (isRotating || rotateTopLeft || rotateBottomLeft || rotateTopRight || rotateBottomRight)
            {
                ROTATE
            }
            else if (xMouse in entity.x - width..entity.x + width && yMouse in entity.y - height..entity.y + height)
            {
                MOVE
            }
            else null

        if (engine.input.hasHoverFocus(context.focusArea))
            cursorType?.let { engine.input.setCursorType(it) }

        if (isRotating || isResizingHorizontally || isResizingVertically)
        {
            entity.set(SIZE_UPDATED)
            entity.set(ROTATION_UPDATED)
            context.notifyTransformChanged(engine, entity, entity::rotation.name, entity::width.name, entity::height.name)
        }
    }

    private fun renderEntityGizmos(surface: Surface, context: ViewportContext)
    {
        val showResizeDots = context.selection.size == 1
        context.selection.forEachFast { entity ->
            if (entity.isNot(HIDDEN) && entity.isSet(EDITABLE))
                renderEntityGizmo(surface, context, entity, showResizeDots)
        }
    }

    private fun renderEntityGizmo(surface: Surface, context: ViewportContext, entity: SceneEntity, showResizeDots: Boolean)
    {
        if (entity !is Spatial2D) return

        val pos = context.camera.worldPosToScreenPos(entity.x, entity.y, 0f, surface.config.width, surface.config.height)
        val padding = gizmoPadding()
        val width = (entity.width + padding * 2f) * context.camera.scale.x / 2f
        val height = (entity.height + padding * 2f) * context.camera.scale.y / 2f
        val size = 4f * UI_SCALE
        val halfSize = size / 2f

        if (entity.rotation != 0f)
        {
            val radians = -entity.rotation / 180f * PI.toFloat()
            val cos = cos(radians)
            val sin = sin(radians)
            val x0 = -width * cos - height * sin
            val y0 = -width * sin + height * cos
            val x1 = width * cos - height * sin
            val y1 = width * sin + height * cos

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
            surface.drawLine(pos.x - width, pos.y - height, pos.x + width, pos.y - height)
            surface.drawLine(pos.x - width, pos.y + height, pos.x + width, pos.y + height)
            surface.drawLine(pos.x - width, pos.y - height, pos.x - width, pos.y + height)
            surface.drawLine(pos.x + width, pos.y - height, pos.x + width, pos.y + height)

            if (showResizeDots)
            {
                surface.setDrawColor(1f, 1f, 1f, 1f)
                surface.drawQuad(pos.x - width - halfSize, pos.y - height - halfSize, size, size)
                surface.drawQuad(pos.x + width - halfSize, pos.y - height - halfSize, size, size)
                surface.drawQuad(pos.x - width - halfSize, pos.y + height - halfSize, size, size)
                surface.drawQuad(pos.x + width - halfSize, pos.y + height - halfSize, size, size)
            }
        }
    }

    private fun renderSelectionRectangle(surface: Surface, context: ViewportContext)
    {
        if (!isSelecting) return

        val pos = context.camera.worldPosToScreenPos(xStartSelect, yStartSelect, 0f, surface.config.width, surface.config.height)
        val width = (xEndSelect - xStartSelect) * context.camera.scale.x
        val height = (yEndSelect - yStartSelect) * context.camera.scale.y
        surface.setDrawColor(1f, 1f, 1f, 0.8f)
        surface.drawLine(pos.x, pos.y, pos.x + width, pos.y)
        surface.drawLine(pos.x, pos.y + height, pos.x + width, pos.y + height)
        surface.drawLine(pos.x, pos.y, pos.x, pos.y + height)
        surface.drawLine(pos.x + width, pos.y, pos.x + width, pos.y + height)
    }

    private fun renderGrid(surface: Surface, context: ViewportContext)
    {
        val camera = context.camera
        val cellSize = 200
        val xStart = (camera.topLeftWorldPosition.x.toInt() / cellSize - 2) * cellSize
        val yStart = (camera.topLeftWorldPosition.y.toInt() / cellSize - 2) * cellSize
        val xEnd = (camera.bottomRightWorldPosition.x.toInt() / cellSize + 1) * cellSize
        val yEnd = (camera.bottomRightWorldPosition.y.toInt() / cellSize + 1) * cellSize
        val middleLineSize = 2f / camera.scale.x
        val alpha = (camera.scale.x + 0.2f).coerceIn(0.1f, 0.4f)
        val shade = 0.1f

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

    private fun renderEntityIcons(surface: Surface, engine: PulseEngine, context: ViewportContext)
    {
        engine.scene.forEachEntityTypeList { entities ->
            val annotation = entities.firstOrNull()?.let { it::class.findAnnotation<Icon>() }
            val firstEntity = entities.firstOrNull()
            if (annotation != null && annotation.showInViewport && firstEntity is Spatial2D)
            {
                val texture = engine.asset.getOrNull<Texture>(annotation.textureAssetName)
                val font = engine.asset.getOrNull<Font>(context.iconFontName)
                val iconChar = context.iconCharacter(annotation.iconName)
                if (texture != null || (font != null && iconChar != null))
                {
                    surface.setDrawColor(Color.WHITE)
                    entities.forEachFast { entity ->
                        if (entity is Spatial2D && entity.isNot(HIDDEN) && entity.isSet(EDITABLE))
                        {
                            val pos = context.camera.worldPosToScreenPos(entity.x, entity.y, 0f, engine.window.width, engine.window.height)
                            if (texture != null)
                            {
                                surface.drawTexture(texture, pos.x, pos.y, annotation.size, annotation.size, 0f, 0.5f, 0.5f)
                            }
                            else if (iconChar != null)
                            {
                                surface.drawText(iconChar, pos.x, pos.y, font, annotation.size, xOrigin = 0.5f, yOrigin = 0.5f)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Spatial2D.isInside(xWorld: Float, yWorld: Float): Boolean
    {
        val padding = gizmoPadding()
        val paddedWidth = abs(width) + padding * 2f
        val paddedHeight = abs(height) + padding * 2f
        val xDiff = xWorld - x
        val yDiff = yWorld - y
        val angle = -MathUtil.atan2(yDiff, xDiff) - rotation / 180f * PI.toFloat()
        val length = sqrt(xDiff * xDiff + yDiff * yDiff)
        val rotatedX = x + cos(angle) * length
        val rotatedY = y + sin(angle) * length
        return rotatedX > x - paddedWidth / 2f && rotatedX < x + paddedWidth / 2f &&
            rotatedY > y - paddedHeight / 2f && rotatedY < y + paddedHeight / 2f
    }

    private fun Spatial2D.isOverlapping(xWorld: Float, yWorld: Float, width: Float, height: Float) =
        x > xWorld && x < xWorld + width && y > yWorld && y < yWorld + height

    private fun isTransforming() = isMoving || isSelecting || isRotating || isResizingVertically || isResizingHorizontally

    private fun gizmoPadding() = GIZMO_PADDING * UI_SCALE

    private fun getIconAngle(startAngle: Float, resizeLeft: Boolean, resizeRight: Boolean, resizeTop: Boolean, resizeBottom: Boolean): Float
    {
        var angle = -startAngle - when
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
        angle %= 360f
        if (angle < 0f) angle += 360f
        return angle
    }

    companion object
    {
        private const val GIZMO_PADDING = 3f
        private const val GRID_SURFACE    = "scene_editor_grid"
        private const val GIZMO_SURFACE   = "scene_editor_gizmo"
        private const val UI_BASE_SURFACE = "scene_editor_ui_base"
    }
}
