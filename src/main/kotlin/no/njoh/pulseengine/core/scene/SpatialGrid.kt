package no.njoh.pulseengine.core.scene

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Array2D
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DISCOVERABLE
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.core.shared.primitives.HitResult
import no.njoh.pulseengine.core.shared.primitives.Physical
import no.njoh.pulseengine.core.shared.primitives.SwapList
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.GridUtil
import no.njoh.pulseengine.core.shared.utils.MathUtil

import kotlin.math.*

class SpatialGrid (
    private val entities : List<SwapList<SceneEntity>>
) {
    var maxWidth = 100_000
    var maxHeight = 100_000
    var borderSize = 3000
    var percentageOfCellsToUpdatePerFrame = 0.2f // Updates 20% of the cells per frame
    var percentagePositionChangeBeforeUpdate = 0.3f // Requires entities to move at least 30% of cellSize before being updated
    var drawGrid = false
    var cellSize = 350f
        private set

    @PublishedApi internal var xOffset = 0f
    @PublishedApi internal var yOffset = 0f
    @PublishedApi internal var xCells = 0
    @PublishedApi internal var yCells = 0
    @PublishedApi internal var invCellSize = 1f / cellSize
    @PublishedApi internal lateinit var cells: Array2D<Node?>

    private var width = 0
    private var height = 0
    private var xMin = 0f
    private var xMax = 0f
    private var yMin = 0f
    private var yMax = 0f
    private var needToRecalculate = false
    private var currentCellIndex = 0
    private val emptyClusterArray = emptyArray<Node?>()
    private val clusterNodes = mutableListOf<Node>()
    private lateinit var scanRanges: IntArray

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
                if (entity.isSet(DISCOVERABLE) && entity.isNot(DEAD))
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

        val xCenter = (xMin + xMax) * 0.5f
        val yCenter = (yMin + yMax) * 0.5f

        width = ((xMax - xMin) + borderSize).toInt()
        height = ((yMax - yMin) + borderSize).toInt()
        xOffset = xCenter - width * 0.5f
        yOffset = yCenter - height * 0.5f
        xCells = (width * invCellSize).toInt()
        yCells = (height * invCellSize).toInt()
        cells = Array2D(xCells, yCells)
        scanRanges = IntArray(2 * yCells) { if (it % 2 == 0) xCells else 0 }
        currentCellIndex = 0

        entities.forEachFast { entities ->
            entities.forEachFast { insert(it) }
        }
    }

    inline fun <reified T> queryArea(x: Float, y: Float, width: Float, height: Float, queryId: Int, block: (T) -> Unit)
    {
        val xCell = ((x - xOffset) * invCellSize).toInt()
        val yCell = ((y - yOffset) * invCellSize).toInt()
        val horizontalNeighbours = 1 + (width * 0.5f * invCellSize).toInt()
        val verticalNeighbours = 1 + (height * 0.5f * invCellSize).toInt()
        val xStartCell = (xCell - horizontalNeighbours).coerceAtLeast(0)
        val yStartCell = (yCell - verticalNeighbours).coerceAtLeast(0)
        val xEndCell = (xStartCell + horizontalNeighbours * 2).coerceAtMost(xCells - 1)
        val yEndCell = (yStartCell + verticalNeighbours * 2).coerceAtMost(yCells - 1)
        val nodes = cells
        lastQueryId = queryId

        for (yi in yStartCell .. yEndCell)
        {
            for (xi in xStartCell .. xEndCell)
            {
                var node = nodes[xi, yi]
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

    inline fun queryRay(x: Float, y: Float, angle: Float, rayLength: Float, rayWidth: Float, queryId: Int, block: (SceneEntity) -> Unit)
    {
        val angleRad = -angle.toRadians()
        val x0 = x - xOffset
        val y0 = y - yOffset
        val x1 = x0 + cos(angleRad) * rayLength
        val y1 = y0 + sin(angleRad) * rayLength
        val nodes = cells
        GridUtil.forEachCellAlongLine(x0, y0, x1, y1, rayWidth, xCells, yCells, cellSize) { xCell, yCell ->
            var node = nodes[xCell, yCell]
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

    inline fun <reified T> queryFirstAlongRay(x: Float, y: Float, angle: Float, rayLength: Float): HitResult<T>?
    {
        val angleRad = -angle.toRadians()
        val xEnd = x + cos(angleRad) * rayLength
        val yEnd = y + sin(angleRad) * rayLength
        var xHit = 0f
        var yHit = 0f
        GridUtil.forEachCellAlongLine(
            x - xOffset, y - yOffset, xEnd - xOffset, yEnd - yOffset, xCells, yCells, cellSize
        ) { xCell, yCell ->
            var node = cells[xCell, yCell]
            var closestEntity: T? = null
            var minDist = Float.MAX_VALUE
            while (node != null)
            {
                val entity = node.entity
                if (entity.isNot(DEAD) && entity is T)
                {
                    val hitPoint = when (entity)
                    {
                        is Physical -> MathUtil.getLineShapeIntersection(x, y, xEnd, yEnd, entity.shape)
                        else -> MathUtil.getLineRectIntersection(
                            x, y, xEnd, yEnd, entity.x, entity.y, entity.width, entity.height, entity.rotation.toRadians()
                        )
                    }

                    if (hitPoint != null && hitPoint.z < minDist)
                    {
                        closestEntity = entity
                        xHit = hitPoint.x
                        yHit = hitPoint.y
                        minDist = hitPoint.z
                    }
                }
                node = node.next
            }

            if (closestEntity != null &&
                ((xHit - xOffset) * invCellSize).toInt() == xCell && // Requires the hit point to be inside the current cell
                ((yHit - yOffset) * invCellSize).toInt() == yCell
            ) {
                return HitResult(closestEntity, xHit, yHit, sqrt(minDist))
            }
        }

        return null
    }


    fun update()
    {
        if (needToRecalculate)
            recalculate()

        val nodes = cells
        val cellSize = cellSize
        val minPosChangeBeforeUpdate = cellSize * percentagePositionChangeBeforeUpdate
        val numberOfCellsToUpdate = (nodes.size * percentageOfCellsToUpdatePerFrame).toInt()
        val startIndex = currentCellIndex
        var endIndex = startIndex + numberOfCellsToUpdate
        currentCellIndex = endIndex

        if (endIndex >= nodes.size)
        {
            currentCellIndex = 0
            endIndex = nodes.size
        }

        for (i in startIndex until endIndex)
        {
            var node = nodes[i]
            while (node != null)
            {
                if (node.cluster != null) // this node is the cluster parent
                {
                    val entity = node.entity
                    if (entity.isSet(DEAD))
                    {
                        removeNode(node)
                    }
                    else if (entity.isAnySet(POSITION_UPDATED or ROTATION_UPDATED or SIZE_UPDATED))
                    {
                        val xDelta = abs(node.xPos - entity.x)
                        val yDelta = abs(node.yPos - entity.y)
                        if (xDelta > minPosChangeBeforeUpdate ||
                            yDelta > minPosChangeBeforeUpdate ||
                            entity.isSet(SIZE_UPDATED) ||
                            (entity.isSet(ROTATION_UPDATED) && node.cluster!!.isNotEmpty())
                        ) {
                            removeNode(node)
                            insert(entity)
                            entity.setNot(POSITION_UPDATED or ROTATION_UPDATED or SIZE_UPDATED)
                        }
                    }
                }
                node = node.next
            }
        }
    }

    fun render(engine: PulseEngine)
    {
        if (!drawGrid)
            return

        val width = xCells * cellSize
        val height = yCells * cellSize
        val surface = engine.gfx.getSurface("spatial_grid") ?: engine.gfx.createSurface(
            name = "spatial_grid",
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.context.zOrder - 1
        )

        // Background rectangle
        surface.setDrawColor(1f, 1f, 1f, 0.1f)
        surface.drawQuad(xOffset, yOffset, width, height)

        for (y in 0 until yCells)
        {
            for (x in 0 until xCells)
            {
                var node = cells[x, y]
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
                    surface.setDrawColor(1f, 1f, 1f, 0.2f)
                    surface.drawQuad(xPos, yPos, cellSize, cellSize)

                    // Cell stroke
                    surface.setDrawColor(1f, 1f, 1f, 0.5f)
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
        while (i < cells.size)
        {
            var node = cells[i]
            while (node != null)
            {
                val next = node.next
                node.next = null
                node.prev = null
                node.cluster = null
                node = next
            }
            cells[i++] = null
        }
    }

    fun setCellSize(cellSize: Float)
    {
        if (this.cellSize == cellSize || cellSize == 0f)
            return

        this.cellSize = cellSize
        this.invCellSize = 1f / cellSize
        this.needToRecalculate = true
    }

    fun insert(entity: SceneEntity)
    {
        if (entity.isNot(DISCOVERABLE) || entity.isSet(DEAD))
            return

        // Check if is entity outside of max area
        if ((entity.x <= xMin && xMin == maxWidth * -0.5f) ||
            (entity.x >= xMax && xMax == maxWidth * 0.5f) ||
            (entity.y <= yMin && yMin == maxHeight * -0.5f) ||
            (entity.y >= yMax && yMax == maxHeight * 0.5f)
        ) {
            entity.setNot(DISCOVERABLE)
            needToRecalculate = true
            return
        }

        val wasInserted = when
        {
            max(abs(entity.width), abs(entity.height)) < cellSize * 0.1 -> insertPoint(entity)
            entity.rotation == 0.0f -> insertAxisAligned(entity)
            else -> insertRotated(entity)
        }

        if (!wasInserted)
            needToRecalculate = true
    }

    private fun insertPoint(entity: SceneEntity): Boolean
    {
        val xCell = ((entity.x - xOffset) * invCellSize).toInt()
        val yCell = ((entity.y - yOffset) * invCellSize).toInt()

        if (xCell < 0 || yCell < 0 || xCell >= xCells || yCell >= yCells)
            return false

        createAndInsertNode(xCell, yCell, entity).cluster = emptyClusterArray
        return true
    }

    private fun insertAxisAligned(entity: SceneEntity): Boolean
    {
        val halfWidth = abs(entity.width) * 0.5f
        val halfHeight = abs(entity.height) * 0.5f
        val x = entity.x - xOffset
        val y = entity.y - yOffset
        val leftCell = ((x - halfWidth) * invCellSize).toInt()
        val rightCell = ((x + halfWidth) * invCellSize).toInt()
        val topCell = ((y - halfHeight) * invCellSize).toInt()
        val bottomCell = ((y + halfHeight) * invCellSize).toInt()

        if (leftCell < 0 || topCell < 0 || rightCell >= xCells || bottomCell >= yCells)
            return false

        val xParentCell = (x * invCellSize).toInt()
        val yParentCell = (y * invCellSize).toInt()
        val maxSize = (bottomCell - topCell + 1) * (rightCell - leftCell + 1) - 1
        val cluster = Array<Node?>(maxSize) { null }
        var parentNode: Node? = null
        var count = 0

        for (yCell in topCell .. bottomCell)
        {
            for (xCell in leftCell .. rightCell)
            {
                if (xCell == xParentCell && yCell == yParentCell)
                    parentNode = createAndInsertNode(xCell, yCell, entity)
                else
                    cluster[count++] = createAndInsertNode(xCell, yCell, entity)
            }
        }

        parentNode?.cluster = cluster
        return true
    }

    private fun insertRotated(entity: SceneEntity): Boolean
    {
        val angle = -entity.rotation.toRadians()
        val halfLength = entity.width * 0.5f
        val thickness = entity.height
        val xDelta = cos(angle) * halfLength
        val yDelta = sin(angle) * halfLength
        val x = entity.x - xOffset
        val y = entity.y - yOffset
        val xStart = x - xDelta
        val yStart = y - yDelta
        val xEnd = x + xDelta
        val yEnd = y + yDelta
        val xParentCell = (x * invCellSize).toInt()
        val yParentCell = (y * invCellSize).toInt()
        var parentNode: Node? = null
        var inserted = false

        GridUtil.forEachCellAlongLine(xStart, yStart, xEnd, yEnd, thickness, xCells, yCells, cellSize, breakOnOutOfBounds = true) { xCell, yCell ->
            val insertedNode = createAndInsertNode(xCell, yCell, entity)
            if (xCell == xParentCell && yCell == yParentCell)
                parentNode = insertedNode
            else
                clusterNodes.add(insertedNode)
            inserted = true
        }

        if (!inserted)
            return false // No entity was inserted, need recalculation

        if (clusterNodes.isEmpty())
            parentNode?.cluster = emptyClusterArray
        else
        {
            parentNode?.cluster = Array(clusterNodes.size) { i -> clusterNodes[i] }
            clusterNodes.clear()
        }

        return true // Entity successfully inserted
    }

    private fun createAndInsertNode(xCell: Int, yCell: Int, entity: SceneEntity): Node
    {
        val node = Node(entity, xCell, yCell, entity.x, entity.y)
        val first = cells[xCell, yCell]
        first?.prev = node
        node.next = first
        node.prev = null
        cells[xCell, yCell] = node
        return node
    }

    private fun removeNode(node: Node)
    {
        if (node.prev == null)
        {
            val first = cells[node.xCell, node.yCell]
            if (first == node)
            {
                first.next?.prev = null
                cells[node.xCell, node.yCell] = first.next
            }
            else println("Entity not first in current cell.. should not happen")
        }
        else
        {
            node.prev?.next = node.next
            node.next?.prev = node.prev
        }

        node.cluster?.forEachFast { if (it != null) removeNode(it) }
    }

    @PublishedApi
    internal inline fun SceneEntity.getQueryId(): Int = (flags ushr 24)

    @PublishedApi
    internal inline fun SceneEntity.setQueryId(id: Int)
    {
        flags = (flags and 16777215) or (id shl 24) // Clear last 8 bits and set queryId
    }

    data class Node(
        val entity: SceneEntity,
        val xCell: Int,
        val yCell: Int,
        val xPos: Float,
        val yPos: Float,
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