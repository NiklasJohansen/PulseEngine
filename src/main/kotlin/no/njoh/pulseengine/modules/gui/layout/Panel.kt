package no.njoh.pulseengine.modules.gui.layout

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.UiElement

open class Panel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var color = Color(1f, 1f, 1f, 0f)
    var strokeColor: Color? = null
    var texture: Texture? = null
    var cornerRadius = 0f

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(surface: Surface2D)
    {
        if (color.alpha != 0f)
        {
            surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
            surface.drawTexture(texture ?: Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius)
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