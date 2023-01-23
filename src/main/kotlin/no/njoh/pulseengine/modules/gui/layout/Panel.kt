package no.njoh.pulseengine.modules.gui.layout

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.gui.*

open class Panel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var color = Color.BLANK
    var strokeColor: Color? = null
    var strokeTop = true
    var strokeBottom = true
    var strokeLeft = true
    var strokeRight = true
    var texture: Texture? = null
    var cornerRadius = ScaledValue.of(0f)

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        if (color.alpha != 0f)
        {
            surface.setDrawColor(color)
            surface.drawTexture(texture ?: Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius.value)
        }

        if (strokeColor != null && strokeColor!!.alpha != 0f)
        {
            surface.setDrawColor(strokeColor!!)
            if (strokeTop)
                surface.drawLine(x.value, y.value, x.value + width.value, y.value)
            if (strokeLeft)
                surface.drawLine(x.value, y.value, x.value, y.value + height.value)
            if (strokeRight)
                surface.drawLine(x.value + width.value, y.value, x.value + width.value, y.value + height.value)
            if (strokeBottom)
                surface.drawLine(x.value, y.value + height.value, x.value + width.value, y.value + height.value)
        }
    }
}