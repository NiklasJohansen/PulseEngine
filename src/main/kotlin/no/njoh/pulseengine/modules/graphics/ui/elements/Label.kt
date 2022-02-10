package no.njoh.pulseengine.modules.graphics.ui.elements

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.modules.asset.types.Font
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.UiElement
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
    var centerHorizontally = false
    var centerVertically = true
    var text = text
        set (value)
        {
            setLayoutDirty()
            field = value
        }

    private var croppedText = text
    var textWidth: Float = 0f
        private set

    init { this.text = text }

    override fun updateChildLayout()
    {
        super.updateChildLayout()
        val charWidths = font.getCharacterWidths(text, fontSize)
        var currentTextWidth = 0f
        for (i in text.indices)
        {
            currentTextWidth += charWidths[i]
            if (currentTextWidth > width.value)
            {
                croppedText = text.substring(0, max(0, i - 2)) + "..."
                return
            }
        }
        croppedText = text
        textWidth = charWidths.sum()
    }

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(surface: Surface2D)
    {
        surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
        surface.drawText(
            text = croppedText,
            x = x.value + if (centerHorizontally) width.value / 2f else 0f,
            y = y.value + if (centerVertically) height.value / 2 else 0f,
            font = font,
            fontSize = fontSize,
            xOrigin = if (centerHorizontally) 0.5f else 0f,
            yOrigin = if (centerVertically) 0.6f else 0f)
    }
}