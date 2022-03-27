package no.njoh.pulseengine.core.shared.utils

import kotlin.math.abs
import kotlin.math.sqrt

object GridUtil
{
    @PublishedApi internal var range = IntArray(0)
    @PublishedApi internal val pos = FloatArray(8)

    /**
     * Calls the [func] function for each cell coordinate in the rectangular area defined by
     * [xStart], [yStart], [xEnd], [yEnd] and [thickness]. Iteration starts from the cell closest
     * to the start point and goes towards the end point.
     *
     * @param xStart The x coordinate of the lines start point
     * @param yStart The y coordinate of the lines start point
     * @param xEnd The x coordinate of the lines end point
     * @param yEnd The y coordinate of the lines end point
     * @param thickness The thickness of the line
     * @param gridWidth The number of horizontal cells in the grid
     * @param gridHeight The number of vertical cells in the grid
     * @param cellSize The size of each grid cell
     * @param breakOnOutOfBounds When true, [func] will never be called if parts of the line is outside the grid
     * @param func The lambda function to call for each cell. Stops searching if the lambda returns false.
     */
    inline fun forEachCellAlongLine(
        xStart: Float,
        yStart: Float,
        xEnd: Float,
        yEnd: Float,
        thickness: Float,
        gridWidth: Int,
        gridHeight: Int,
        cellSize: Float,
        breakOnOutOfBounds: Boolean = false,
        func: (x: Int, y: Int) -> Unit
    ) {
        val xDiff = xEnd - xStart
        val yDiff = yEnd - yStart
        val length = 1f / sqrt(xDiff * xDiff + yDiff * yDiff).coerceAtLeast(0.0001f) * thickness * 0.5f
        val xNormal = -yDiff * length
        val yNormal = xDiff * length

        // Set corner positions
        val pos = pos
        pos[0] = xStart - xNormal
        pos[1] = yStart - yNormal
        pos[2] = xStart + xNormal
        pos[3] = yStart + yNormal
        pos[4] = xEnd + xNormal
        pos[5] = yEnd + yNormal
        pos[6] = xEnd - xNormal
        pos[7] = yEnd - yNormal

        // Create a new array for horizontal min and max values if necessary
        if (2 * gridHeight != range.size)
            range = IntArray(2 * gridHeight) { if (it % 2 == 0) gridWidth else 0 }

        val range = range
        var yMin = range.size
        var yMax = 0
        var i = 0
        while (i < 8)
        {
            val x0 = pos[i + 0]
            val y0 = pos[i + 1]
            val x1 = pos[(i + 2) % 8]
            val y1 = pos[(i + 3) % 8]

            // Find cells along each edge and update horizontal range values
            val insideGrid = forEachCellAlongLine(x0, y0, x1, y1, gridWidth, gridHeight, cellSize) { x: Int, y: Int ->
                if (x < range[y * 2]) range[y * 2] = x
                if (x > range[y * 2 + 1]) range[y * 2 + 1] = x
                if (y < yMin) yMin = y
                if (y > yMax) yMax = y
            }

            // Reset horizontal range values and return if line is out of bounds
            if (!insideGrid && breakOnOutOfBounds)
            {
                for (y in yMin .. yMax)
                {
                    range[y * 2] = Int.MAX_VALUE
                    range[y * 2 + 1] = 0
                }
                return
            }

            i += 2
        }

        // Iterate through each row
        for (y in yMin .. yMax)
        {
            // Reverse y direction if ray points upwards
            val y0 = if (yDiff > 0) y else (yMax - (y - yMin))
            val xMin = range[y0 * 2]
            val xMax = range[y0 * 2 + 1]

            // Iterate through each cell in the row
            for (x in xMin .. xMax)
               func(if (xDiff > 0) x else (xMax - (x - xMin)), y0) // Reverse x direction if ray points left

            // Reset horizontal range values
            range[y0 * 2] = Int.MAX_VALUE
            range[y0 * 2 + 1] = 0
        }
    }

    /**
     * Calls the [func] function for each cell coordinate along the line defined
     * by [xStart], [yStart], [xEnd] and [yEnd].
     *
     * @param xStart The x position of the lines start coordinate
     * @param yStart The y position of the lines start coordinate
     * @param xEnd The x position of the lines end coordinate
     * @param yEnd The x position of the lines end coordinate
     * @param gridWith The number of horizontal cells in the grid
     * @param gridHeight The number of vertical cells in the grid
     * @param cellSize The size of each grid cell
     * @param func The lambda function to call for each cell. Stops searching if the lambda returns false.
     * @return true if the whole line is inside the grid
     */
    inline fun forEachCellAlongLine(
        xStart: Float,
        yStart: Float,
        xEnd: Float,
        yEnd: Float,
        gridWith: Int,
        gridHeight: Int,
        cellSize: Float,
        func: (x: Int, y: Int) -> Unit
    ): Boolean {
        val xDelta = xEnd - xStart
        val yDelta = yEnd - yStart
        val xStepSize = cellSize / abs(xDelta)
        val yStepSize = cellSize / abs(yDelta)
        var xCell = (xStart / cellSize).toInt()
        var yCell = (yStart / cellSize).toInt()

        // Set start condition and step direction
        var xDistance = ((if (xDelta < 0) xCell else xCell + 1) * cellSize - xStart) / xDelta
        var yDistance = ((if (yDelta < 0) yCell else yCell + 1) * cellSize - yStart) / yDelta
        val xStep = if (xDelta < 0) -1 else 1
        val yStep = if (yDelta < 0) -1 else 1

        // Call function for first cell if it is inside the grid
        var insideGrid = xCell >= 0 && xCell < gridWith && yCell >= 0 && yCell < gridHeight
        if (insideGrid)
            func(xCell, yCell)

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
                func(xCell, yCell)
            else
                insideGrid = false
        }

        return insideGrid
    }
}