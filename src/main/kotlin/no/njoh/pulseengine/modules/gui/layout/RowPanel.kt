package no.njoh.pulseengine.modules.gui.layout

import no.njoh.pulseengine.core.shared.utils.Extensions.sumIf
import no.njoh.pulseengine.modules.gui.*
import no.njoh.pulseengine.modules.gui.ScrollbarVisibility.*
import kotlin.math.max

class RowPanel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : Panel(x, y, width, height), VerticallyScrollable {

    override var renderOnlyInside = true
    override var verticalScrollbarVisibility = ONLY_VISIBLE_WHEN_NEEDED
    override var yScroll = 0

    private var scrollFraction = 0f
    private var cachedUsedSpace = -1f

    override fun updateChildLayout()
    {
        val usedSpace = calculateUsedSpace(recalculate = true)
        val availableSpace = height.value
        val scrollSpace = max(usedSpace - availableSpace, 0f)
        var yPos = y.value - scrollFraction * scrollSpace

        for (child in children)
        {
            child.setLayoutClean()
            if (child.hidden)
                continue

            val yChild = child.y.calculate(minVal = yPos + child.padding.top, maxVal = yPos)
            val isOutsideBounds = yChild + child.height.value < y.value || yChild > y.value + height.value
            child.preventRender(isOutsideBounds)

            if (!isOutsideBounds)
            {
                val widthChild = width.value - (child.padding.left + child.padding.right)
                val xChild = child.x.calculate(minVal = x.value + child.padding.left, maxVal = x.value + width.value - widthChild)

                child.width.setQuiet(widthChild)
                child.x.setQuiet(xChild)
                child.y.setQuiet(yChild)
                child.updateLayout()
            }

            yPos += child.height.value + child.padding.top + child.padding.bottom
        }
    }

    override fun setVerticalScroll(fraction: Float)
    {
        this.scrollFraction = fraction
        this.setLayoutDirty()
    }

    override fun getVerticallyUsedSpaceFraction(): Float
    {
        val availableSpace = max(1f, height.value)
        return calculateUsedSpace(recalculate = false) / availableSpace
    }

    private fun calculateUsedSpace(recalculate: Boolean): Float {
        if (recalculate)
            cachedUsedSpace = children.sumIf({ !it.hidden }) { it.height.value + it.padding.top + it.padding.bottom }
        return cachedUsedSpace
    }
}