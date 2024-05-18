package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.modules.gui.*

open class Icon(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    init { focusable = false }

    var color = Color.WHITE
    var iconFontName = ""
    var iconCharacter = ""
    var iconSize = ScaledValue.of(15f)

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        val iconFont = engine.asset.getOrNull<Font>(iconFontName) ?: return
        surface.setDrawColor(color)
        surface.drawText(
            text = iconCharacter,
            x = x.value + width.value * 0.5f,
            y = y.value + height.value * 0.5f,
            font = iconFont,
            fontSize = iconSize.value,
            xOrigin = 0.5f,
            yOrigin = 0.5f
        )
    }
}