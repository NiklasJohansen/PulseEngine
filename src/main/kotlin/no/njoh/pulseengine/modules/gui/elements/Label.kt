package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.modules.gui.*
import no.njoh.pulseengine.modules.gui.elements.Label.TextSizeStrategy.*
import kotlin.math.max

class Label(
    text: String,
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    init { focusable = false }

    var color = Color.WHITE
    var font = Font.DEFAULT
    var centerHorizontally = false
    var centerVertically = true
    var textResizeStrategy = CROP_TEXT
    var fontSize = ScaledValue.of(24f)
    var text = text
        set (value)
        {
            if (value != field)
                setLayoutDirty()
            field = value
        }

    private var visibleText = text
    private var lastText = text
    private var lastFontSize = fontSize
    private var lastWidth = width.value
    private var charWidths = FloatArray(0)
    var textWidth: Float = 0f
        private set

    override fun onCreate(engine: PulseEngine)
    {
        updateTextWidths()
    }

    override fun updateChildLayout()
    {
        super.updateChildLayout()

        val wasTextWidthUpdate = updateTextWidths()

        if (wasTextWidthUpdate || width.value != lastWidth)
        {
            var currentText = text
            when (textResizeStrategy)
            {
                NONE -> { }
                CROP_TEXT ->
                {
                    var i = 0
                    var currentTextWidth = 0f
                    while (i < text.length && i < charWidths.size)
                    {
                        currentTextWidth += charWidths[i]
                        if (currentTextWidth > width.value)
                        {
                            currentText = text.substring(0, max(0, i - 2)) + "..."
                            break
                        }
                        i++
                    }
                }
                UPDATE_WIDTH ->
                {
                    width.type = Size.ValueType.ABSOLUTE
                    width.value = textWidth
                }
            }

            visibleText = currentText
            lastWidth = width.value
        }
    }

    private fun updateTextWidths(): Boolean
    {
        return if (text != lastText || fontSize != lastFontSize)
        {
            charWidths = font.getCharacterWidths(text, fontSize.value, useCache = true)
            textWidth = charWidths.sum()
            lastText = text
            lastFontSize = fontSize
            true
        }
        else false
    }

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
        surface.drawText(
            text = visibleText,
            x = x.value + if (centerHorizontally) width.value / 2f else 0f,
            y = y.value + if (centerVertically) height.value / 2 else 0f,
            font = font,
            fontSize = fontSize.value,
            xOrigin = if (centerHorizontally) 0.5f else 0f,
            yOrigin = if (centerVertically) 0.5f else 0f)
    }

    enum class TextSizeStrategy
    {
        NONE, CROP_TEXT, UPDATE_WIDTH
    }
}