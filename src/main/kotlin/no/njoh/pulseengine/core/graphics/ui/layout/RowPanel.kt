package no.njoh.pulseengine.core.graphics.ui.layout

import no.njoh.pulseengine.core.graphics.ui.Position
import no.njoh.pulseengine.core.graphics.ui.Scrollable
import no.njoh.pulseengine.core.graphics.ui.Size
import kotlin.math.max

class RowPanel (
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : Panel(x, y, width, height), Scrollable {

    var rowHeight = 30f
    var rowPadding = 10f
    override var scrollFraction: Float = 0f
    override var hideScrollbarOnEnoughSpaceAvailable = true

    override fun updateChildLayout()
    {
        val visibleChildrenCount = children.count { !it.hidden }
        val availableSpaceCount = (height.value / (rowHeight + rowPadding)).toInt()
        val rowScroll = (scrollFraction * max(0, visibleChildrenCount - availableSpaceCount)).toInt()
        var yPos = y.value - rowScroll * (rowHeight + rowPadding)

        for (child in children)
        {
            val widthChild = width.value - (child.padding.left + child.padding.right)
            val heightChild = rowHeight - (child.padding.top + child.padding.bottom)

            val xChild = child.x.calculate(x.value + child.padding.left, x.value + width.value - widthChild)
            val yChild = child.y.calculate(yPos + child.padding.top, yPos + rowHeight - heightChild)
            val isOutsideBounds = yChild < y.value || yChild + heightChild > y.value + height.value

            child.width.setQuiet(widthChild)
            child.height.setQuiet(heightChild)
            child.x.setQuiet(xChild)
            child.y.setQuiet(yChild)
            child.preventRender(isOutsideBounds)
            child.setLayoutClean()
            child.updateLayout()

            if (!child.hidden)
                yPos += rowHeight + rowPadding
        }
    }

    override fun setScroll(fraction: Float)
    {
        this.scrollFraction = fraction
        this.setLayoutDirty()
    }

    override fun getUsedSpaceFraction(): Float
    {
        val visibleChildrenCount = children.count { !it.hidden }
        val neededSpace = -rowPadding + visibleChildrenCount * (rowHeight + rowPadding)
        val availableSpace = max(1f, height.value)
        return neededSpace / availableSpace
    }
}