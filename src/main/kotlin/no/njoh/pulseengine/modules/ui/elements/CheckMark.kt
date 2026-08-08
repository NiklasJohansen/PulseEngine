package no.njoh.pulseengine.modules.ui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.ui.Position
import no.njoh.pulseengine.modules.ui.Size
import no.njoh.pulseengine.modules.ui.UiElement

class CheckMark(
    private val isChecked: () -> Boolean,
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var color = Color.WHITE

    init { focusable = false }

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        if (!isChecked())
            return

        val xStart  = x.value + width.value  * 0.28f
        val xMiddle = x.value + width.value  * 0.43f
        val xEnd    = x.value + width.value  * 0.72f
        val yStart  = y.value + height.value * 0.52f
        val yMiddle = y.value + height.value * 0.67f
        val yEnd    = y.value + height.value * 0.34f

        surface.setDrawColor(color)
        surface.drawLine(xStart, yStart, xMiddle, yMiddle)
        surface.drawLine(xMiddle, yMiddle, xEnd, yEnd)
    }
}