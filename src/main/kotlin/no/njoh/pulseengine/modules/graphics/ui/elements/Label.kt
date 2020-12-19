package no.njoh.pulseengine.modules.graphics.ui.elements

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.assets.Font
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import kotlin.math.max

class Label(
    text: String,
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var color = Color(1f, 1f, 1f)
    var font = Font.DEFAULT
    var fontSize = 24f
    var text = text
        set (value)
        {
            setLayoutDirty()
            field = value
        }

    var croppedText = text

    init { this.text = text }

    override fun updateChildLayout()
    {
        super.updateChildLayout()
        val charWidths = font.getCharacterWidths(text, fontSize)
        var textWidth = 0f
        for (i in text.indices)
        {
            textWidth += charWidths[i]
            if (textWidth > width.value)
            {
                croppedText = text.substring(0, max(0, i - 2)) + "..."
                return
            }
        }
        croppedText = text
    }

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(surface: Surface2D)
    {
        surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
        surface.drawText(croppedText, x.value, y.value + height.value / 2, font, fontSize = fontSize, yOrigin = 0.5f)
    }
}