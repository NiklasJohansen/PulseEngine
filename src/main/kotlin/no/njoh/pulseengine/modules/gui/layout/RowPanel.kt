package no.njoh.pulseengine.modules.gui.layout

import no.njoh.pulseengine.core.shared.utils.Extensions.sumIf
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Scrollable
import no.njoh.pulseengine.modules.gui.Size
import kotlin.math.max

class RowPanel (
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : Panel(x, y, width, height), Scrollable {

    override var renderOnlyInside = true
    override var scrollFraction = 0f
    override var hideScrollbarOnEnoughSpaceAvailable = true

    override fun updateChildLayout()
    {
        val usedSpace = calculateUsedSpace()
        val availableSpace = height.value
        val scrollSpace = max(usedSpace - availableSpace, 0f)
        var yPos = y.value - scrollFraction * scrollSpace

        for (child in children)
        {
            val widthChild = width.value - (child.padding.left + child.padding.right)
            val xChild = child.x.calculate(minVal = x.value + child.padding.left, maxVal = x.value + width.value - widthChild)
            val yChild = child.y.calculate(minVal = yPos + child.padding.top, maxVal = yPos)
            val isOutsideBounds = yChild + child.height.value < y.value || yChild > y.value + height.value

            child.width.setQuiet(widthChild)
            child.x.setQuiet(xChild)
            child.y.setQuiet(yChild)
            child.preventRender(isOutsideBounds)
            child.setLayoutClean()
            child.updateLayout()

            if (!child.hidden)
                yPos += child.height.value + child.padding.top + child.padding.bottom
        }
    }

    override fun setScroll(fraction: Float)
    {
        this.scrollFraction = fraction
        this.setLayoutDirty()
    }

    override fun getUsedSpaceFraction(): Float
    {
        val availableSpace = max(1f, height.value)
        return calculateUsedSpace() / availableSpace
    }

    private fun calculateUsedSpace() =
        children.sumIf({ !it.hidden }) { it.height.value + it.padding.top + it.padding.bottom }
}