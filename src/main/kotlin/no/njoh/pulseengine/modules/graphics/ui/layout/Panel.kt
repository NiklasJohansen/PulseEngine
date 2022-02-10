package no.njoh.pulseengine.modules.graphics.ui.layout

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.modules.asset.types.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.UiElement

open class Panel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var color = Color(1f, 1f, 1f, 0f)
    var strokeColor: Color? = null
    var texture: Texture? = null

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(surface: Surface2D)
    {
        if (color.alpha != 0f)
        {
            surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
            surface.drawTexture(texture ?: Texture.BLANK, x.value, y.value, width.value, height.value)
        }

        if (strokeColor != null && strokeColor!!.alpha != 0f)
        {
            surface.setDrawColor(strokeColor!!)
            surface.drawLine(x.value, y.value, x.value + width.value, y.value)
            surface.drawLine(x.value, y.value, x.value, y.value + height.value)
            surface.drawLine(x.value + width.value, y.value, x.value + width.value, y.value + height.value)
            surface.drawLine(x.value, y.value + height.value, x.value + width.value, y.value + height.value)
        }
    }
}