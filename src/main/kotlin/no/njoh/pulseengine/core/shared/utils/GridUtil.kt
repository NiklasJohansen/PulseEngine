package no.njoh.pulseengine.core.shared.utils

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object GridUtil
{
    @PublishedApi internal var range = IntArray(0)
    @PublishedApi internal val pos = FloatArray(8)

    /**
     * Calls the [action] function for each cell coordinate in the rectangular area defined by
     * [x0], [y0], [x1], [y1] and [thickness]. Iteration starts from the cell closest
     * to the start point and goes towards the end point.
     *
     * @param x0 The x coordinate of the lines start point
     * @param y0 The y coordinate of the lines start point
     * @param x1 The x coordinate of the lines end point
     * @param y1 The y coordinate of the lines end point
     * @param thickness The thickness of the line
     * @param gridWidth The number of horizontal cells in the grid
     * @param gridHeight The number of vertical cells in the grid
     * @param cellSize The size of each grid cell
     * @param breakOnOutOfBounds When true, [action] will never be called if parts of the line is outside the grid
     * @param action The lambda function to call for each cell. Stops searching if the lambda returns false.
     */
    inline fun forEachCellAlongLineInsideGrid(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        thickness: Float,
        gridWidth: Int,
        gridHeight: Int,
        cellSize: Float,
        breakOnOutOfBounds: Boolean = false,
        action: (x: Int, y: Int) -> Unit
    ) {
        // Set corner positions
        val xDiff = x1 - x0
        val yDiff = y1 - y0
        val invLength = 1f / max(0.0001f, sqrt(xDiff * xDiff + yDiff * yDiff)) * thickness * 0.5f
        val xNormal = -yDiff * invLength
        val yNormal = xDiff * invLength
        val pos = pos
        pos[0] = x0 - xNormal
        pos[1] = y0 - yNormal
        pos[2] = x0 + xNormal
        pos[3] = y0 + yNormal
        pos[4] = x1 + xNormal
        pos[5] = y1 + yNormal
        pos[6] = x1 - xNormal
        pos[7] = y1 - yNormal

        // Create a new array for horizontal min and max values if necessary
        if (2 * gridHeight != range.size)
            range = IntArray(2 * gridHeight) { if (it % 2 == 0) Int.MAX_VALUE else 0 }

        val range = range
        var yMin = range.size
        var yMax = 0
        var xLast = pos[6]
        var yLast = pos[7]
        var i = 0
        while (i < 8)
        {
            val x = pos[i]
            val y = pos[i + 1]
            var insideGrid = true

            // Find cells along each edge and update horizontal and vertical range values
            forEachCellAlongLine(x, y, xLast, yLast, cellSize) { xCell: Int, yCell: Int ->
                if (yCell >= 0 && yCell < gridHeight)
                {
                    // Find min and max xCell. Clamps xCell to grid size
                    if (xCell < range[yCell * 2]) range[yCell * 2] = max(xCell, 0)
                    if (xCell > range[yCell * 2 + 1]) range[yCell * 2 + 1] = min(xCell, gridWidth - 1)

                    // Find min and max yCell
                    if (yCell < yMin) yMin = yCell
                    if (yCell > yMax) yMax = yCell

                    if (xCell < 0 || xCell >= gridWidth)
                        insideGrid = false
                }
                else insideGrid = false
            }

            // Reset horizontal range values and return if line is out of bounds
            if (!insideGrid && breakOnOutOfBounds)
            {
                for (yCell in yMin .. yMax)
                {
                    range[yCell * 2] = Int.MAX_VALUE
                    range[yCell * 2 + 1] = 0
                }
                return
            }

            xLast = x
            yLast = y
            i += 2
        }

        // Iterate through each row
        for (yCell in yMin .. yMax)
        {
            // Reverse y direction if ray points upwards
            val yc = if (yDiff > 0) yCell else (yMax - (yCell - yMin))
            val xMin = range[yc * 2]
            val xMax = range[yc * 2 + 1]

            // Iterate through each cell in the row
            for (xCell in xMin .. xMax)
               action(if (xDiff > 0) xCell else (xMax - (xCell - xMin)), yc) // Reverse x direction if ray points left

            // Reset horizontal range values
            range[yc * 2] = Int.MAX_VALUE
            range[yc * 2 + 1] = 0
        }
    }

    /**
     * Calls the [action] function for each cell coordinate along the line defined by [x0], [y0], [x1]
     * and [y1].
     *
     * @param x0 The x position of the lines start coordinate
     * @param y0 The y position of the lines start coordinate
     * @param x1 The x position of the lines end coordinate
     * @param y1 The x position of the lines end coordinate
     * @param cellSize The size of each grid cell
     * @param action The lambda function to call for each cell. Stops searching if the lambda returns false.
     */
    inline fun forEachCellAlongLine(x0: Float, y0: Float, x1: Float, y1: Float, cellSize: Float, action: (x: Int, y: Int) -> Unit)
    {
        val xDelta = x1 - x0
        val yDelta = y1 - y0
        val xStepSize = cellSize / abs(xDelta)
        val yStepSize = cellSize / abs(yDelta)
        var xCell = (x0 / cellSize).toInt()
        var yCell = (y0 / cellSize).toInt()
        var xDistance = ((if (xDelta < 0) xCell else xCell + 1) * cellSize - x0) / xDelta
        var yDistance = ((if (yDelta < 0) yCell else yCell + 1) * cellSize - y0) / yDelta
        val xStep = if (xDelta < 0) -1 else 1
        val yStep = if (yDelta < 0) -1 else 1

        // Call [action] for the starting cell
        action(xCell, yCell)

        while (xDistance <= 1f || yDistance <= 1f)
        {
            if (xDistance < yDistance)
            {
                xCell += xStep
                xDistance += xStepSize
            }
            else
            {
                yCell += yStep
                yDistance += yStepSize
            }
            action(xCell, yCell)
        }
    }

    /**
     * Calls the [action] function for each cell coordinate along the line defined by [x0], [y0], [x1]
     * and [y1]. Will not call [action] with coordinates outside the grid dimensions.
     *
     * @param x0 The x position of the lines start coordinate
     * @param y0 The y position of the lines start coordinate
     * @param x1 The x position of the lines end coordinate
     * @param y1 The x position of the lines end coordinate
     * @param gridWith The number of horizontal cells in the grid
     * @param gridHeight The number of vertical cells in the grid
     * @param cellSize The size of each grid cell
     * @param action The lambda function to call for each cell. Stops searching if the lambda returns false.
     */
    inline fun forEachCellAlongLineInsideGrid(x0: Float, y0: Float, x1: Float, y1: Float, cellSize: Float, gridWith: Int, gridHeight: Int, action: (x: Int, y: Int) -> Unit)
    {
        val xDelta = x1 - x0
        val yDelta = y1 - y0
        val xStepSize = cellSize / abs(xDelta)
        val yStepSize = cellSize / abs(yDelta)
        var xCell = (x0 / cellSize).toInt()
        var yCell = (y0 / cellSize).toInt()
        var xDistance = ((if (xDelta < 0) xCell else xCell + 1) * cellSize - x0) / xDelta
        var yDistance = ((if (yDelta < 0) yCell else yCell + 1) * cellSize - y0) / yDelta
        val xStep = if (xDelta < 0) -1 else 1
        val yStep = if (yDelta < 0) -1 else 1

        // Call [action] for first cell if it is inside the grid
        if (xCell >= 0 && xCell < gridWith && yCell >= 0 && yCell < gridHeight)
            action(xCell, yCell)

        while (xDistance <= 1f || yDistance <= 1f)
        {
            if (xDistance < yDistance)
            {
                xCell += xStep
                xDistance += xStepSize
            }
            else
            {
                yCell += yStep
                yDistance += yStepSize
            }

            if (xCell >= 0 && xCell < gridWith && yCell >= 0 && yCell < gridHeight)
                action(xCell, yCell)
        }
    }
}