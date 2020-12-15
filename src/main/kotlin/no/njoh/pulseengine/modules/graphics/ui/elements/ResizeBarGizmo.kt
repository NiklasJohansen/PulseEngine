package no.njoh.pulseengine.modules.graphics.ui.elements

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.CursorType.*
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import kotlin.math.PI
import kotlin.math.sin

class ResizeBarGizmo(
    private val isVertical: Boolean,
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
): UiElement(x, y, width, height) {

    var positionDiff = 0.001f
    private var fade = 0f
    private var resizing = false
    private var cursorSet = false

    override fun onUpdate(engine: PulseEngine)
    {
        val mouseInside = area.isInside(engine.input.xMouse, engine.input.yMouse)
        if (mouseInside)
        {
            if (!resizing && engine.input.isPressed(Mouse.LEFT))
                resizing = true

            if (!cursorSet)
            {
                val cursor = if (isVertical) HORIZONTAL_RESIZE else VERTICAL_RESIZE
                engine.input.setCursor(cursor)
                cursorSet = true
            }
        }
        else if (cursorSet && !engine.input.isPressed(Mouse.LEFT))
        {
            engine.input.setCursor(ARROW)
            cursorSet = false
        }

        if (resizing)
        {
            if (!engine.input.isPressed(Mouse.LEFT))
                resizing = false

            positionDiff += if (isVertical) engine.input.xdMouse else engine.input.ydMouse
            setLayoutDirty()
        }

        fade = (fade + (if (mouseInside || resizing) 0.08f else -0.08f)).coerceIn(0f, 1f)
    }

    override fun onRender(surface: Surface2D)
    {
        if (fade > 0f)
        {
            val alpha = (1f + sin(fade * PI - PI / 2f).toFloat()) / 2f
            surface.setDrawColor(0.4f, 0.4f, 0.7f, alpha * 0.8f)
            surface.drawTexture(Texture.BLANK, area.x0, area.y0, area.width, area.height)
        }
    }
}