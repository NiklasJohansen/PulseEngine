package no.njoh.pulseengine.modules.graphics.ui.layout

import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.Size.ValueType.*
import no.njoh.pulseengine.util.sumByFloat
import no.njoh.pulseengine.util.sumIf
import kotlin.math.max
import kotlin.math.min

class VerticalPanel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : Panel(x, y, width, height) {

    override fun updateChildLayout()
    {
        val requiredAbsoluteSpace = children.sumIf({ !it.hidden && it.height.type == ABSOLUTE }, { it.height.value }) + children.sumByFloat { it.padding.top + it.padding.bottom }
        val availableRelativeSpace = max(0f, height.value - requiredAbsoluteSpace)
        val fractionSum = min(1f, children.sumIf({ !it.hidden }, { it.height.fraction }))
        val requiredRelativeSpace = availableRelativeSpace * fractionSum
        val countAutoChildren = children.count { !it.hidden && it.height.type == AUTO }
        val availableAutoSpace = max(0f, availableRelativeSpace - requiredRelativeSpace) / countAutoChildren
        var yPos = y.value

        for (child in children)
        {
            child.setLayoutClean()
            if (child.hidden)
                continue

            val availableWidth = width.value - (child.padding.left + child.padding.right)
            val availableHeight = when (child.height.type)
            {
                ABSOLUTE -> 0f // Uses its own value
                RELATIVE -> availableRelativeSpace
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