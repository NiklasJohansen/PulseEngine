package no.njoh.pulseengine.modules.gui.layout

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.CursorType.*
import no.njoh.pulseengine.core.input.MouseButton
import no.njoh.pulseengine.core.input.Input
import no.njoh.pulseengine.core.window.Window
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.Size.ValueType.*
import kotlin.math.abs
import kotlin.math.max

open class WindowPanel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : Panel(x, y, width, height) {

    val header = Panel(height = Size.absolute(30f))
    val body = VerticalPanel()

    var movable = false
    var resizable = false
    var resizeMargin = 10f
    var isGrabbed = false
        private set

    private var isResizingTop = false
    private var isResizingBottom = false
    private var isResizingRight = false
    private var isResizingLeft = false
    private var lastIsInsideTopResizeArea = false
    private var lastIsInsideBottomResizeArea = false
    private var lastIsInsideLeftResizeArea = false
    private var lastIsInsideRightResizeArea = false

    override fun onCreate(engine: PulseEngine)
    {
        header.focusable = false
        header.id = id + "_header"
        body.focusable = false
        body.id = id + "_body"

        val content = VerticalPanel()
        content.focusable = false
        content.addChildren(header, body)
        addChildren(content)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        handleMovingAndResizing(engine.input, engine.window)
    }

    private fun handleMovingAndResizing(input: Input, window: Window)
    {
        val allowResize = parent !is VerticalPanel && parent !is HorizontalPanel && resizable
        val isInsideArea = area.isInside(input.xMouse, input.yMouse)
        val isInsideHeader = header.area.isInside(input.xMouse, input.yMouse)
        val isMouseMoving = abs(input.xdMouse) > 1f || abs(input.ydMouse) > 1f
        val isInsideTopResizeArea = allowResize && isInsideArea && (input.yMouse < area.y0 + resizeMargin)
        val isInsideBottomResizeArea = allowResize && isInsideArea && (input.yMouse > area.y1 - resizeMargin)
        val isInsideLeftResizeArea = allowResize && isInsideArea && (input.xMouse < area.x0 + resizeMargin)
        val isInsideRightResizeArea = allowResize && isInsideArea && (input.xMouse > area.x1 - resizeMargin)

        if (input.isPressed(MouseButton.LEFT))
        {
            if (!isGrabbed && !isResizingTop && !isResizingBottom && !isResizingLeft && !isResizingRight && isInsideArea)
            {
                when
                {
                    isInsideTopResizeArea -> isResizingTop = true
                    isInsideBottomResizeArea -> isResizingBottom = true
                    isInsideLeftResizeArea -> isResizingLeft = true
                    isInsideRightResizeArea -> isResizingRight = true
                    isInsideHeader && isMouseMoving -> isGrabbed = true
                }
            }
        }
        else
        {
            isGrabbed = false
            isResizingTop = false
            isResizingBottom = false
            isResizingLeft = false
            isResizingRight = false
        }

        if (movable && isGrabbed)
        {
            x.value = max(0f, x.value + input.xdMouse)
            y.value = max(0f, y.value + input.ydMouse)
        }

        when
        {
            isResizingLeft   -> resizeLeft(input.xdMouse, window.width.toFloat())
            isResizingRight  -> resizeRight(input.xdMouse, window.width.toFloat())
            isResizingTop    -> resizeTop(input.ydMouse, window.height.toFloat())
            isResizingBottom -> resizeBottom(input.ydMouse, window.height.toFloat())
        }

        if (isResizingTop || isResizingBottom || isInsideTopResizeArea || isInsideBottomResizeArea)
            input.setCursorType(VERTICAL_RESIZE)

        if (isResizingLeft || isResizingRight || isInsideRightResizeArea || isInsideLeftResizeArea)
            input.setCursorType(HORIZONTAL_RESIZE)

        if (!isResizingTop && isInsideTopResizeArea != lastIsInsideTopResizeArea)
        {
            lastIsInsideTopResizeArea = isInsideTopResizeArea
            if (!isInsideTopResizeArea)
                input.setCursorType(ARROW)
        }

        if (!isResizingBottom && isInsideBottomResizeArea != lastIsInsideBottomResizeArea)
        {
            lastIsInsideBottomResizeArea = isInsideBottomResizeArea
            if (!isInsideBottomResizeArea)
                input.setCursorType(ARROW)
        }

        if (!isResizingLeft && isInsideLeftResizeArea != lastIsInsideLeftResizeArea)
        {
            lastIsInsideLeftResizeArea = isInsideLeftResizeArea
            if (!isInsideLeftResizeArea)
                input.setCursorType(ARROW)
        }

        if (!isResizingRight && isInsideRightResizeArea != lastIsInsideRightResizeArea)
        {
            lastIsInsideRightResizeArea = isInsideRightResizeArea
            if (!isInsideRightResizeArea)
                input.setCursorType(ARROW)
        }
    }

    private fun resizeLeft(xdMouse: Float, windowWidth: Float)
    {
        when (width.type)
        {
            RELATIVE ->
            {
                val change = -xdMouse / (parent?.width?.value ?: windowWidth)
                width.fraction = (width.fraction + change).coerceIn(0.001f, 1f)
                x.value += xdMouse
                setLayoutDirty()
            }
            ABSOLUTE ->
            {
                width.value = width.value - xdMouse
                x.value += xdMouse
                setLayoutDirty()
            }
            AUTO -> width.convertToRelativeType(parent?.width?.value ?: windowWidth)
        }
    }

    private fun resizeRight(xdMouse: Float, windowWidth: Float)
    {
        when (width.type)
        {
            RELATIVE ->
            {
                val change = xdMouse / (parent?.width?.value ?: windowWidth)
                width.fraction = (width.fraction + change).coerceIn(0.001f, 1f)
                setLayoutDirty()
            }
            ABSOLUTE ->
            {
                width.value += xdMouse
                setLayoutDirty()
            }
            AUTO -> width.convertToRelativeType(parent?.width?.value ?: windowWidth)
        }
    }

    private fun resizeTop(ydMouse: Float, windowHeight: Float)
    {
        when (height.type)
        {
            RELATIVE ->
            {
                val change = -ydMouse / (parent?.height?.value ?: windowHeight)
                height.fraction = (height.fraction + change).coerceIn(0.001f, 1f)
                y.value += ydMouse
                setLayoutDirty()
            }
            ABSOLUTE ->
            {
                height.value -= ydMouse
                y.value += ydMouse
                setLayoutDirty()
            }
            AUTO -> height.convertToRelativeType(parent?.height?.value ?: windowHeight)
        }
    }

    private fun resizeBottom(ydMouse: Float, windowHeight: Float)
    {
        when (height.type)
        {
            RELATIVE ->
            {
                val change = ydMouse / (parent?.height?.value ?: windowHeight)
                height.fraction = (height.fraction + change).coerceIn(0.001f, 1f)
                setLayoutDirty()
            }
            ABSOLUTE ->
            {
                height.value += ydMouse
                setLayoutDirty()
            }
            AUTO -> height.convertToRelativeType(parent?.height?.value ?: windowHeight)
        }
    }

    private fun Size.convertToRelativeType(maxSize: Float)
    {
        this.updateType(RELATIVE)
        this.fraction = this.value / maxSize
    }
}