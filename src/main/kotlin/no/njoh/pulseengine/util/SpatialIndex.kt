package no.njoh.pulseengine.util

import no.njoh.pulseengine.data.Array2D
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.SceneEntity

class SpatialIndex (
    var xOffset: Float,
    var yOffset: Float,
    var width: Float,
    var height: Float,
    var cellSize: Float
) {
    private var xCells = (width / cellSize).toInt()
    private var yCells = (height / cellSize).toInt()

    private val items: Array2D<SceneEntity?> = Array2D(xCells, yCells) { x, y -> null }
    private val iterator = EntityIterator(null)
    private val multiIterator = MultiEntityIterator()

    fun query(x: Float, y: Float): EntityIterator
    {
        val xCell = ((x - xOffset) / cellSize).toInt()
        val yCell = ((y - yOffset) / cellSize).toInt()

        if (xCell >= 0 && xCell < xCells && yCell >= 0 && yCell < yCells)
            return iterator.set(items[xCell, yCell])

        return iterator
    }

    fun queryRadius(x: Float, y: Float, radius: Float): MultiEntityIterator
    {
        val xCell = ((x - xOffset) / cellSize).toInt()
        val yCell = ((y - yOffset) / cellSize).toInt()
        val neighbourCells = 1 + (radius / cellSize).toInt()
        val xStart = (xCell - neighbourCells).coerceAtLeast(0)
        val yStart = (yCell - neighbourCells).coerceAtLeast(0)
        val xEnd = (xStart + neighbourCells * 2) .coerceAtMost(xCells - 1)
        val yEnd = (yStart + neighbourCells * 2).coerceAtMost(yCells - 1)

        multiIterator.clear()

        for (yi in yStart .. yEnd)
            for (xi in xStart .. xEnd)
                items[xi, yi]?.let { multiIterator.add(it) }

        return multiIterator
    }

    fun insert(entity: SceneEntity)
    {
        val xCell = ((entity.x - xOffset) / cellSize).toInt()
        val yCell = ((entity.y - yOffset) / cellSize).toInt()

        if (xCell >= 0 && xCell < xCells && yCell >= 0 && yCell < yCells)
        {
            val first = items[xCell, yCell]
            first?.prev = entity
            entity.next = first
            entity.prev = null
            items[xCell, yCell] = entity
        }
    }

    fun remove(entity: SceneEntity)
    {
        if (entity.prev == null)
        {
            val xCell = ((entity.x - xOffset) / cellSize).toInt()
            val yCell = ((entity.y - yOffset) / cellSize).toInt()

            if (xCell >= 0 && xCell < xCells && yCell >= 0 && yCell < yCells)
            {
                val first = items[xCell, yCell]
                if (first == entity)
                {
                    first.next?.prev = null
                    items[xCell, yCell] = entity
                }
                else
                {
                    println("Entity not first in current cell..")
                }
            }
        }
        else if (entity.next != null) {
            entity.prev?.next = entity.next
        }
    }

    fun render(surface: Surface2D)
    {
        if (!draw)
            return

        surface.setDrawColor(1f, 1f, 1f, 0.5f)

        for (x in 0 until  xCells)
            surface.drawLine(xOffset + x * cellSize, yOffset, xOffset + x * cellSize, yOffset + height)

        for (y in 0 until yCells)
            surface.drawLine(xOffset, yOffset + y * cellSize, xOffset + width, yOffset + y * cellSize)

        surface.drawLine(xOffset, yOffset, xOffset + width, yOffset)
        surface.drawLine(xOffset, yOffset + height, xOffset + width, yOffset + height)
        surface.drawLine(xOffset, yOffset, xOffset, yOffset + height)
        surface.drawLine(xOffset + width, yOffset, xOffset + width, yOffset + height)
    }


    class EntityIterator(
        var entity: SceneEntity?
    ) : Iterator<SceneEntity> {

        fun set(entity: SceneEntity?): EntityIterator
        {
            this.entity = entity
            return this
        }

        override fun hasNext() =
            entity != null

        override fun next(): SceneEntity
        {
            val current = entity
            entity = entity?.next
            return current!!
        }
    }

    class MultiEntityIterator : Iterator<SceneEntity>
    {
        var items = Array<SceneEntity?>(50) { null }
        var currentItem: SceneEntity? = null
        var size = 0
        var index = 0

        fun clear()
        {
            size = 0
            index = 0
            currentItem = null
//            for(i in 0 until size)
//                items[i] = null
        }

        fun add(entity: SceneEntity)
        {
            if (currentItem == null)
                currentItem = entity

            items[size] = entity
            size++
            if (size >= items.size)
                items = Array(items.size * 2) { null }
        }

        override fun hasNext() =
            currentItem != null && index < size

        override fun next(): SceneEntity
        {
            val next = currentItem
            currentItem = when
            {
                currentItem?.next != null -> currentItem?.next
                index + 1 < size          -> items[++index]
                else                      -> null
            }
            return next!!
        }
    }


    companion object {
        var draw = true
    }

}