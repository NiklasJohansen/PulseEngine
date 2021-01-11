package no.njoh.pulseengine.modules.graphics.ui.layout

import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.Size.ValueType.*
import no.njoh.pulseengine.util.sumByFloat
import no.njoh.pulseengine.util.sumIf
import kotlin.math.max

class HorizontalPanel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : Panel(x, y, width, height) {

    init { focusable = false }

    override fun updateChildLayout()
    {
        val requiredAbsoluteSpace = children.sumIf({ !it.hidden && it.width.type == ABSOLUTE }, { it.width.value }) + children.sumByFloat { it.padding.left + it.padding.right }
        val availableRelativeSpace = max(0f, width.value - requiredAbsoluteSpace)
        val fractionSum = children.sumIf({ !it.hidden && it.width.type == RELATIVE }) { it.width.fraction }
        val requiredRelativeSpace = availableRelativeSpace * fractionSum.coerceAtMost(1f)
        val countAutoChildren = children.count { !it.hidden && it.width.type == AUTO }
        val availableAutoSpace = max(0f, availableRelativeSpace - requiredRelativeSpace) / countAutoChildren
        var xPos = x.value

        for (child in children)
        {
            child.setLayoutClean()
            if (child.hidden)
                continue

            val availableHeight = height.value - (child.padding.top + child.padding.bottom)
            val availableWidth = when (child.width.type)
            {
                ABSOLUTE -> 0f // Uses width.value
                RELATIVE -> availableRelativeSpace / fractionSum.coerceAtLeast(1f)
                AUTO -> availableAutoSpace
            }

            val widthChild = child.width.calculate(availableWidth)
            val heightChild = child.height.calculate(availableHeight)
            val xChild = child.x.calculate(xPos + child.padding.left, xPos + availableWidth - widthChild)
            val yChild = child.y.calculate(y.value + child.padding.top, y.value + availableHeight - heightChild)

            child.width.setQuiet(widthChild)
            child.height.setQuiet(heightChild)
            child.x.setQuiet(xChild)
            child.y.setQuiet(yChild)
            child.updateLayout()

            xPos += widthChild + child.padding.left + child.padding.right
        }

        if (popup is HorizontalResizeGizmo)
            popup?.updateLayout()
    }
}