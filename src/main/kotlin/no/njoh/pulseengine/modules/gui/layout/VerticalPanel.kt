package no.njoh.pulseengine.modules.gui.layout

import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.Size.ValueType.*
import no.njoh.pulseengine.core.shared.utils.Extensions.sumIf
import no.njoh.pulseengine.modules.gui.plus
import kotlin.math.max

class VerticalPanel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : Panel(x, y, width, height) {

    init { focusable = false }

    override fun updateChildLayout()
    {
        val requiredPadding = children.sumIf({ it.isVisible() }, { it.padding.top + it.padding.bottom })
        val requiredAbsoluteSpace = children.sumIf({ it.isVisible() && it.height.type == ABSOLUTE }, { it.height.value })
        val availableRelativeSpace = max(0f, height.value - requiredAbsoluteSpace - requiredPadding)
        val fractionSum = children.sumIf({ it.isVisible() && it.height.type == RELATIVE }) { it.height.fraction }
        val requiredRelativeSpace = availableRelativeSpace * fractionSum.coerceAtMost(1f)
        val countAutoChildren = children.count { it.isVisible() && it.height.type == AUTO }
        val availableAutoSpace = max(0f, availableRelativeSpace - requiredRelativeSpace) / countAutoChildren
        var yPos = y.value

        for (child in children)
        {
            child.setLayoutClean()
            if (!child.isVisible())
                continue

            val availableWidth = width.value - (child.padding.left + child.padding.right)
            val availableHeight = when (child.height.type)
            {
                ABSOLUTE -> 0f // Uses height.value
                RELATIVE -> availableRelativeSpace / fractionSum.coerceAtLeast(1f)
                AUTO -> availableAutoSpace
            }

            val widthChild = child.width.calculate(availableWidth)
            val heightChild = child.height.calculate(availableHeight)
            val xChild = child.x.calculate(x.value + child.padding.left, x.value + availableWidth - widthChild)
            val yChild = child.y.calculate(yPos + child.padding.top, yPos + availableHeight - heightChild)

            child.width.setQuiet(widthChild)
            child.height.setQuiet(heightChild)
            child.x.setQuiet(xChild)
            child.y.setQuiet(yChild)
            child.updateLayout()

            yPos += heightChild + child.padding.top + child.padding.bottom
        }
    }
}