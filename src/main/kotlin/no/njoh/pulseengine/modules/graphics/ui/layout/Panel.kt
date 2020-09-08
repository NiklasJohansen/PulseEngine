package no.njoh.pulseengine.modules.graphics.ui.layout

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.CursorType.*
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.InputInterface
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.Size.ValueType.ABSOLUTE
import no.njoh.pulseengine.modules.graphics.ui.Size.ValueType.RELATIVE
import no.njoh.pulseengine.modules.graphics.ui.elements.UiElement
import kotlin.math.max

open class Panel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var color = Color(1f, 1f, 1f, 0f)
    var texture: Texture? = null
    var movable = false
    var resizable = false
    var resizeMargin = 10f

    private var isGrabbed = false
    private var isResizingVertical = false
    private var isResizingHorizontal = false
    private var lastIsInsideVerticalResizeArea = false
    private var lastIsInsideHorizontalResizeArea = false

    override fun onUpdate(engine: PulseEngine)
    {
        handleMovingAndResizing(engine.input)
    }

    private fun handleMovingAndResizing(input: InputInterface)
    {
        // TODO: add resize on top and left side (need to invert direction...)
        val isInsideArea = area.isInside(input.xMouse, input.yMouse)
        val isInsideVerticalResizeArea = resizable && isInsideArea && (input.yMouse > area.y1 - resizeMargin)
        val isInsideHorizontalResizeArea = resizable && isInsideArea && (input.xMouse > area.x1 - resizeMargin)

        if (input.isPressed(Mouse.LEFT))
        {
            if (!isGrabbed && !isResizingVertical && !isResizingHorizontal && isInsideArea)
            {
                if (isInsideVerticalResizeArea)
                    isResizingVertical = true
                else if (isInsideHorizontalResizeArea)
                    isResizingHorizontal = true
                else
                    isGrabbed = true
            }
        }
        else
        {
            isGrabbed = false
            isResizingVertical = false
            isResizingHorizontal = false
        }

        if (movable && isGrabbed)
        {
            x.value = max(0f, x.value + input.xdMouse)
            y.value = max(0f, y.value + input.ydMouse)
        }

        if (isResizingHorizontal)
        {
            if (width.type == RELATIVE)
            {
                width.fraction = (width.fraction + input.xdMouse * 0.001f).coerceIn(0.001f, 1f)
                setLayoutDirty()
            }
            else if (width.type == ABSOLUTE)
                width.value = width.value + input.xdMouse
        }

        if (isResizingVertical)
        {
            if (height.type == RELATIVE)
            {
                height.fraction = (height.fraction + input.ydMouse * 0.001f).coerceIn(0.001f, 1f)
                setLayoutDirty()
            }
            else if (height.type == ABSOLUTE)
                height.value = height.value + input.ydMouse
        }

        if ((isResizingVertical || isInsideVerticalResizeArea) && (height.type == RELATIVE || height.type == ABSOLUTE))
            input.setCursor(VERTICAL_RESIZE)

        if ((isResizingHorizontal || isInsideHorizontalResizeArea) && (width.type == RELATIVE || width.type == ABSOLUTE))
            input.setCursor(HORIZONTAL_RESIZE)

        if (!isResizingVertical && isInsideVerticalResizeArea != lastIsInsideVerticalResizeArea)
        {
            lastIsInsideVerticalResizeArea = isInsideVerticalResizeArea
            if (!isInsideVerticalResizeArea)
                input.setCursor(ARROW)
        }

        if (!isResizingHorizontal && isInsideHorizontalResizeArea != lastIsInsideHorizontalResizeArea)
        {
            lastIsInsideHorizontalResizeArea = isInsideHorizontalResizeArea
            if (!isInsideHorizontalResizeArea)
                input.setCursor(ARROW)
        }
    }

    override fun onRender(surface: Surface2D)
    {
        surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
        surface.drawTexture(texture ?: Texture.BLANK, x.value, y.value, width.value, height.value)
    }
}