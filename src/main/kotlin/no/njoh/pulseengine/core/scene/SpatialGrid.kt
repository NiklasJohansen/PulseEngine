package no.njoh.pulseengine.core.scene

import no.njoh.pulseengine.core.shared.primitives.Array2D
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.entities.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.entities.SceneEntity.Companion.DISCOVERABLE
import no.njoh.pulseengine.core.scene.entities.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.entities.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.entities.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.core.shared.primitives.SwapList
import no.njoh.pulseengine.core.scene.entities.SceneEntity
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import kotlin.math.*

class SpatialGrid (
    private val entities : List<SwapList<SceneEntity>>,
    var cellSize: Float,
    var minBorderSize: Int = 3000,
    var maxWidth: Int = 100_000,
    var maxHeight: Int = 100_000,
    var percentageToUpdatePerFrame: Float = 1f
) {
    @PublishedApi internal var xOffset = 0f
    @PublishedApi internal var yOffset = 0f
    @PublishedApi internal var xCells = 0
    @PublishedApi internal var yCells = 0
    @PublishedApi internal lateinit var array: Array2D<Node?>

    private var width = 0
    private var height = 0
    private var xMin = 0f
    private var xMax = 0f
    private var yMin = 0f
    private var yMax = 0f

    private var needToRecalculate = false
    private var currentNodeIndex = 0
    private val cornerPositions = FloatArray(8)
    private val emptyClusterArray = emptyArray<Node?>()
    private lateinit var scanRanges: IntArray
    var drawGrid = false

    init { recalculate() }

    fun recalculate()
    {
        needToRecalculate = false
        xMin = Float.POSITIVE_INFINITY
        xMax = Float.NEGATIVE_INFINITY
        yMin = Float.POSITIVE_INFINITY
        yMax = Float.NEGATIVE_INFINITY

        entities.forEachFast { entities ->
            entities.forEachFast { entity ->
                if (entity.isNot(DEAD))
                {
                    val r = 0.5f * if (entity.width > entity.height) entity.width else entity.height
                    if (entity.x + r > xMax) xMax = entity.x + r
                    if (entity.x - r < xMin) xMin = entity.x - r
                    if (entity.y + r > yMax) yMax = entity.y + r
                    if (entity.y - r < yMin) yMin = entity.y - r
                }
            }
        }

        // Set default area on empty entity collections
        if (xMin == Float.POSITIVE_INFINITY)
        {
            xMin = 5 * -cellSize
            xMax = 5 * cellSize
            yMin = xMin
            yMax = xMax
        }

        xMin = max(xMin, maxWidth * -0.5f)
        xMax = min(xMax, maxWidth * 0.5f)
        yMin = max(yMin, maxHeight * -0.5f)
        yMax = min(yMax, maxHeight * 0.5f)

        val xCenter = (xMin + xMax) / 2
        val yCenter = (yMin + yMax) / 2

        width = ((xMax - xMin) + minBorderSize).toInt()
        height = ((yMax - yMin) + minBorderSize).toInt()
        xOffset = xCenter - width / 2f
        yOffset = yCenter - height / 2f
        xCells = (width / cellSize).toInt()
        yCells = (height / cellSize).toInt()
        array = Array2D(xCells, yCells) { x, y -> null }
        scanRanges = IntArray(2 * yCells) { if (it % 2 == 0) xCells else 0 }

        entities.forEachFast { entities ->
            entities.forEachFast { insert(it) }
        }
    }

    inline fun query(x: Float, y: Float, width: Float, height: Float, queryId: Int, block: (SceneEntity) -> Unit)
    {
        val xCell = ((x - xOffset) / cellSize).toInt()
        val yCell = ((y - yOffset) / cellSize).toInt()
        val horizontalNeighbours = 1 + (width / 2f / cellSize).toInt()
        val verticalNeighbours = 1 + (height / 2f / cellSize).toInt()
        val xStart = (xCell - horizontalNeighbours).coerceAtLeast(0)
        val yStart = (yCell - verticalNeighbours).coerceAtLeast(0)
        val xEnd = (xStart + horizontalNeighbours * 2).coerceAtMost(xCells - 1)
        val yEnd = (yStart + verticalNeighbours * 2).coerceAtMost(yCells - 1)
        val array = array
        lastQueryId = queryId

        for (yi in yStart .. yEnd)
        {
            for (xi in xStart .. xEnd)
            {
                var node = array[xi, yi]
                while (node != null)
                {
                    val entity = node.entity
                    if (entity.isNot(DEAD) && entity.getQueryId() != queryId)
                    {
                        block(entity)
                        entity.setQueryId(queryId)
                    }
                    node = node.next
                }
            }
        }
    }

    inline fun <reified T> queryType(x: Float, y: Float, width: Float, height: Float, queryId: Int, block: (T) -> Unit)
    {
        val invCellSize = 1.0f / cellSize
        val xCell = ((x - xOffset) * invCellSize).toInt()
        val yCell = ((y - yOffset) * invCellSize).toInt()
        val horizontalNeighbours = 1 + (width * 0.5f * invCellSize).toInt()
        val verticalNeighbours = 1 + (height * 0.5f * invCellSize).toInt()
//        val xStart = (xCell - horizontalNeighbours).coerceAtLeast(0)
//        val yStart = (yCell - verticalNeighbours).coerceAtLeast(0)
//        val xEnd = (xStart + horizontalNeighbours * 2).coerceAtMost(xCells - 1)
//        val yEnd = (yStart + verticalNeighbours * 2).coerceAtMost(yCells - 1)

        var xStart = xCell - horizontalNeighbours
        var yStart = yCell - verticalNeighbours
        var xEnd = xStart + horizontalNeighbours * 2
        var yEnd = yStart + verticalNeighbours * 2

        if (xStart < 0) xStart = 0
        if (yStart < 0) yStart = 0
        if (xEnd > xCells - 1) xEnd = xCells - 1
        if (yEnd > yCells - 1) yEnd = yCells - 1

        val array = array
        lastQueryId = queryId

        for (yi in yStart .. yEnd)
        {
            for (xi in xStart .. xEnd)
            {
                var node = array[xi, yi]
                while (node != null)
                {
                    val entity = node.entity
                    if (entity.isNot(DEAD) && entity.getQueryId() != queryId && entity is T)
                    {
                        block(entity as T)
                        entity.setQueryId(queryId)
                    }
                    node = node.next
                }
            }
        }
    }

    @PublishedApi
    internal inline fun SceneEntity.getQueryId(): Int = (flags ushr 24)

    @PublishedApi
    internal inline fun SceneEntity.setQueryId(id: Int)
    {
        flags = (flags and 16777215) or (id shl 24) // Clear last 8 bits and set queryId
    }

    fun insert(entity: SceneEntity): Boolean
    {
        if (entity.isSet(DEAD))
            return false

        if ((entity.x <= xMin && xMin == maxWidth * -0.5f) ||
            (entity.x >= xMax && xMax == maxWidth * 0.5f) ||
            (entity.y <= yMin && yMin == maxHeight * -0.5f) ||
            (entity.y >= yMax && yMax == maxHeight * 0.5f)
        ) {
            entity.set(DEAD)
            needToRecalculate = true
            return false
        }

        val inserted = when
        {
            entity.isNot(DISCOVERABLE) -> true
            abs(entity.width) + abs(entity.height) < cellSize * 0.5 -> insertPoint(entity)
            entity.rotation == 0.0f -> insertAxisAligned(entity)
            else -> insertRotated(entity)
        }

        if (!inserted)
            needToRecalculate = true

        return inserted
    }


    private fun insertPoint(entity: SceneEntity): Boolean
    {
        val x = ((entity.x - xOffset)  / cellSize).toInt()
        val y = ((entity.y - yOffset)  / cellSize).toInt()

        if (x < 0 || y < 0 || x >= xCells || y >= yCells)
            return false

        insert(x, y, entity).cluster = emptyClusterArray
        return true
    }

    private fun insertAxisAligned(entity: SceneEntity): Boolean
    {
        val halfWidth = abs(entity.width) / 2f
        val halfHeight = abs(entity.height) / 2f
        val left = ((entity.x - halfWidth - xOffset)  / cellSize).toInt()
        val right = ((entity.x + halfWidth - xOffset)  / cellSize).toInt()
        val top = ((entity.y - halfHeight - yOffset)  / cellSize).toInt()
        val bottom = ((entity.y + halfHeight - yOffset) / cellSize).toInt()

        if (left < 0 || top < 0 || right >= xCells || bottom >= yCells)
            return false

        val xCell = ((entity.x - xOffset) / cellSize).toInt()
        val yCell = ((entity.y - yOffset) / cellSize).toInt()
        val maxSize = (bottom - top + 1) * (right - left + 1) - 1
        val cluster = Array<Node?>(maxSize) { null }
        var mainCell: Node? = null
        var count = 0

        for (y in top .. bottom)
        {
            for (x in left .. right)
            {
                if (x == xCell && y == yCell)
                    mainCell = insert(x, y, entity)
                else
                    cluster[count++] = insert(x, y, entity)
            }
        }

        mainCell?.cluster = cluster
        return true
    }

    private fun insertRotated(entity: SceneEntity): Boolean
    {
        val rot = -entity.rotation / 180 * PI.toFloat()
        val c = cos(rot)
        val s = sin(rot)
        val w = entity.width / 2f
        val h = entity.height / 2f
        val x = entity.x
        val y = entity.y
        val x0 = -w * c - h * s
        val y0 = -w * s + h * c
        val x1 = w * c - h * s
        val y1 = w * s + h * c

        cornerPositions[0] = x + x0 - xOffset
        cornerPositions[1] = y + y0 - yOffset
        cornerPositions[2] = x + x1 - xOffset
        cornerPositions[3] = y + y1 - yOffset
        cornerPositions[4] = x - x0 - xOffset
        cornerPositions[5] = y - y0 - yOffset
        cornerPositions[6] = x - x1 - xOffset
        cornerPositions[7] = y - y1 - yOffset

        var yMin = yCells
        var yMax = 0
        var i = 0
        while (i < 4)
        {
            val xCorner0 = cornerPositions[(i * 2 + 0)]
            val yCorner0 = cornerPositions[(i * 2 + 1)]
            val xCorner1 = cornerPositions[(i * 2 + 2) % 8]
            val yCorner1 = cornerPositions[(i * 2 + 3) % 8]

            var xCell = floor(xCorner0 / cellSize).toInt()
            var yCell = floor(yCorner0 / cellSize).toInt()
            if (yCell < 0 || yCell >= yCells)
                return false

            scanRanges[yCell * 2] = min(scanRanges[yCell * 2], xCell)
            scanRanges[yCell * 2 + 1] = max(scanRanges[yCell * 2 + 1], xCell)
            yMin = min(yMin, yCell)
            yMax = max(yMax, yCell)

            val xCornerDelta = xCorner1 - xCorner0
            val yCornerDelta = yCorner1 - yCorner0
            val dsx = if (xCornerDelta != 0f) cellSize / abs(xCornerDelta) else 0f
            val dsy = if (yCornerDelta != 0f) cellSize / abs(yCornerDelta) else 0f
            var sx = 10f
            var sy = 10f

            if (xCornerDelta < 0) sx = (cellSize * xCell - xCorner0) / xCornerDelta
            if (xCornerDelta > 0) sx = (cellSize * (xCell + 1) - xCorner0) / xCornerDelta
            if (yCornerDelta < 0) sy = (cellSize * yCell - yCorner0) / yCornerDelta
            if (yCornerDelta > 0) sy = (cellSize * (yCell + 1) - yCorner0) / yCornerDelta

            while (sx <= 1 || sy <= 1)
            {
                if (sx < sy)
                {
                    sx += dsx
                    if (xCornerDelta > 0) xCell++ else xCell--
                }
                else
                {
                    sy += dsy
                    if (yCornerDelta > 0) yCell++ else yCell--
                }

                if (yCell < 0 || yCell >= yCells)
                    return false

                scanRanges[yCell * 2] = min(scanRanges[yCell * 2], xCell)
                scanRanges[yCell * 2 + 1] = max(scanRanges[yCell * 2 + 1], xCell)
                yMin = min(yMin, yCell)
                yMax = max(yMax, yCell)
            }

            i++
        }

        val xParent = ((entity.x - xOffset) / cellSize).toInt()
        val yParent = ((entity.y - yOffset) / cellSize).toInt()

        var maxSize = 0
        for (yi in yMin .. yMax)
            maxSize += scanRanges[yi * 2 + 1] - scanRanges[yi * 2] + 1

        val cluster = Array<Node?>(maxSize - 1) { null }
        var mainCell: Node? = null
        var count = 0

        for (yi in yMin .. yMax)
        {
            val xMin = scanRanges[yi * 2]
            val xMax = scanRanges[yi * 2 + 1]

            // clear scan ranges
            scanRanges[yi * 2] = xCells
            scanRanges[yi * 2 + 1] = 0

            if (xMin < 0 || xMin >= xCells || xMax < 0 || xMax >= xCells)
                return false

            for (xi in xMin .. xMax)
            {
                if (xi == xParent && yi == yParent)
                    mainCell = insert(xi, yi, entity)
                else
                    cluster[count++] = insert(xi, yi, entity)
            }
        }

        mainCell?.cluster = cluster
        return true
    }

    private fun insert(xCell: Int, yCell: Int, entity: SceneEntity): Node
    {
        val node = Node(entity, xCell, yCell)
        val first = array[xCell, yCell]
        first?.prev = node
        node.next = first
        node.prev = null
        array[xCell, yCell] = node
        return node
    }

    private fun remove(node: Node)
    {
        if (node.prev == null)
        {
            val first = array[node.xCell, node.yCell]
            if (first == node)
            {
                first.next?.prev = null
                array[node.xCell, node.yCell] = first.next
            }
            else println("Entity not first in current cell.. should not happen")
        }
        else
        {
            node.prev?.next = node.next
            node.next?.prev = node.prev
        }

        node.cluster?.forEach { if (it != null) remove(it) }
    }

    fun update()
    {
        if (needToRecalculate)
            recalculate()

        val nodesToUpdate = (array.size * percentageToUpdatePerFrame).toInt()
        val start = currentNodeIndex
        var end = start + nodesToUpdate
        currentNodeIndex = end

        if (end >= array.size)
        {
            currentNodeIndex = 0
            end = array.size
        }

        val invCellSize = 1.0f / cellSize
        for (i in start until end)
        {
            var node = array[i]
            while (node != null)
            {
                if (node.cluster != null) // this node is the cluster parent
                {
                    val entity = node.entity
                    if (entity.isSet(DEAD))
                    {
                        remove(node)
                    }
                    else if (entity.isAnySet(POSITION_UPDATED or ROTATION_UPDATED or SIZE_UPDATED))
                    {
                        val xEntity = ((entity.x - xOffset) * invCellSize).toInt()
                        val yEntity = ((entity.y - yOffset) * invCellSize).toInt()

                        if (node.xCell != xEntity || node.yCell != yEntity || entity.isSet(SIZE_UPDATED) ||
                            (node.cluster!!.isNotEmpty() && entity.isSet(ROTATION_UPDATED)))
                        {
                            remove(node)
                            insert(entity)
                            entity.setNot(POSITION_UPDATED or ROTATION_UPDATED or SIZE_UPDATED)
                        }
                    }
                }
                node = node.next
            }
        }
    }

    fun render(surface: Surface2D)
    {
        if (!drawGrid)
            return

        val width = xCells * cellSize
        val height = yCells * cellSize

        // Background rectangle
        surface.setDrawColor(1f, 1f, 1f, 0.3f)
        surface.drawQuad(xOffset, yOffset, width, height)

        for (y in 0 until yCells)
        {
            for (x in 0 until xCells)
            {
                var node = array[x, y]
                if (node != null)
                {
                    var count = 0
                    while (node != null)
                    {
                        node = node.next
                        count++
                    }

                    val xPos = x * cellSize + xOffset
                    val yPos = y * cellSize + yOffset

                    // Entity count text
                    surface.setDrawColor(1f, 1f, 1f, 1f)
                    surface.drawText(count.toString(), xPos + 10f, yPos + 10f, fontSize = 30f)

                    // Cell quad
                    surface.setDrawColor(1f, 1f, 1f, 0.3f)
                    surface.drawQuad(xPos, yPos, cellSize, cellSize)

                    // Cell stroke
                    surface.setDrawColor(1f, 1f, 1f, 0.7f)
                    surface.drawLine(xPos, yPos, xPos + cellSize, yPos)
                    surface.drawLine(xPos, yPos + cellSize, xPos + cellSize, yPos + cellSize)
                    surface.drawLine(xPos, yPos, xPos, yPos + cellSize)
                    surface.drawLine(xPos + cellSize, yPos, xPos + cellSize, yPos + cellSize)
                }
            }
        }

        // Border stroke
        surface.setDrawColor(1f, 1f, 1f, 0.8f)
        surface.drawLine(xOffset, yOffset, xOffset + width, yOffset)
        surface.drawLine(xOffset, yOffset + height, xOffset + width, yOffset + height)
        surface.drawLine(xOffset, yOffset, xOffset, yOffset + height)
        surface.drawLine(xOffset + width, yOffset, xOffset + width, yOffset + height)
    }

    fun clear()
    {
        var i = 0
        while (i < array.size)
        {
            var node = array[i]
            while (node != null)
            {
                node.cluster = null
                node.prev = null
                node = node.next
                node?.next = null
            }
            array[i++] = null
        }
    }

    data class Node(
        val entity: SceneEntity,
        val xCell: Int,
        val yCell: Int,
        var prev: Node? = null,
        var next: Node? = null,
        var cluster: Array<Node?>? = null
    )

    companion object
    {
        var lastQueryId = 1
        fun nextQueryId() = (++lastQueryId) % 255
    }
}