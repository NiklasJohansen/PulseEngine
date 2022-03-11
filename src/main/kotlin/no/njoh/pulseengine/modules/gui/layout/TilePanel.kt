package no.njoh.pulseengine.modules.gui.layout

import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Scrollable
import no.njoh.pulseengine.modules.gui.Size
import kotlin.math.max
import kotlin.math.min

class TilePanel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : Panel(x, y, width, height), Scrollable {

    var horizontalTiles = 5
    var tilePadding = 10f
    var maxTileSize = 100f

    private var rows = 0
    private var currentTileSize = 0f
    private var currentPaddingSize = 0f
    override var scrollFraction = 0f
    override var hideScrollbarOnEnoughSpaceAvailable = false

    override fun updateChildLayout()
    {
        currentPaddingSize = tilePadding * (2 + horizontalTiles - 1) / horizontalTiles
        currentTileSize = min(maxTileSize, width.value / horizontalTiles - currentPaddingSize)

        val availableSpaceCount = (height.value / (currentTileSize + currentPaddingSize)).toInt()
        val rowScroll = (scrollFraction * max(0, rows - availableSpaceCount)).toInt()
        var yPos = y.value - rowScroll * (currentTileSize + tilePadding)
        var xPos = x.value

        rows = 1
        for (child in children)
        {
            val widthChild = currentTileSize - (child.padding.left + child.padding.right)
            val heightChild = currentTileSize - (child.padding.top + child.padding.bottom)
            val xChild = child.x.calculate(xPos + child.padding.left + tilePadding)
            val yChild = child.y.calculate(yPos + child.padding.top + tilePadding)
            val isHidden = yChild < y.value || yChild + heightChild > y.value + height.value

            child.width.setQuiet(widthChild)
            child.height.setQuiet(heightChild)
            child.x.setQuiet(xChild)
            child.y.setQuiet(yChild)
            child.preventRender(isHidden)
            child.setLayoutClean()
            child.updateLayout()

            val offset = widthChild + child.padding.left + child.padding.right + tilePadding
            xPos += offset

            if (xPos + offset > x.value + width.value)
            {
                xPos = x.value
                yPos += currentTileSize + tilePadding
                rows++
            }
        }
    }

    override fun setScroll(fraction: Float)
    {
        scrollFraction = fraction
        setLayoutDirty()
    }

    override fun getUsedSpaceFraction(): Float
    {
        val neededSpace = rows * (currentTileSize + currentPaddingSize)
        val availableSpace = max(1f, height.value)
        return neededSpace / availableSpace
    }
}