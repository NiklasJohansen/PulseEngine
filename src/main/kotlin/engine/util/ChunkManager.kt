package engine.util

import engine.modules.graphics.CameraInterface
import engine.modules.graphics.GraphicsInterface
import engine.modules.graphics.Surface2D
import org.joml.Vector2f
import java.lang.IllegalStateException
import kotlin.math.ceil
import kotlin.math.floor

class ChunkManager <T: Chunk> (
    val chunkSize: Int = 1000,
    private val minSurroundingLoadedChunkBorder: Int = 5,
    private val activeOfScreenChunkBorder: Int = 1
) {
    private lateinit var loadedChunks: Array<Array<Any>>
    private lateinit var onChunkLoadCallback: (x: Int, y: Int) -> T
    private lateinit var onChunkSaveCallback: (chunk: T, x: Int, y: Int) -> Unit
    private lateinit var debugSurface: Surface2D

    private var yChunkCount: Int = 5 + minSurroundingLoadedChunkBorder * 2
    private var xChunkCount: Int = 5 + minSurroundingLoadedChunkBorder * 2
    private var xOffsetIndex = -xChunkCount / 2
    private var yOffsetIndex = -yChunkCount / 2
    private var xStart: Int = 0
    private var yStart: Int = 0
    private var xEnd: Int = 0
    private var yEnd: Int = 0

    private val iterator = ChunkIterator()
    private var lastRecalculateTime = 0L
    private var recalculateArraySizeInterval = 5000

    fun setOnChunkLoad(callback: (x: Int, y: Int) -> T)
    {
        this.onChunkLoadCallback = callback
    }

    fun setOnChunkSave(callback: (chunk: T, xIndex: Int, yIndex: Int) -> Unit)
    {
        this.onChunkSaveCallback = callback
    }

    @Suppress("UNCHECKED_CAST")
    fun init()
    {
        if (!this::onChunkLoadCallback.isInitialized)
            throw IllegalStateException("onChunkLoad callback has not been set for ChunkManager")

        if (!this::onChunkSaveCallback.isInitialized)
            throw IllegalStateException("onChunkSave callback has not been set for ChunkManager")

        loadedChunks =
            IntRange(0, yChunkCount).map { y ->
                IntRange(0, xChunkCount).map { x ->
                    onChunkLoadCallback.invoke(x + xOffsetIndex, y + yOffsetIndex) as Any
                }.toTypedArray()
            }.toTypedArray()
    }

    fun update(camera: CameraInterface)
    {
        calculateArrayCoordinates(camera.topLeftWorldPosition, camera.bottomRightWorldPosition)
        calculateArrayOffset(camera.topLeftWorldPosition, camera.bottomRightWorldPosition)
        clampArrayCoordinatesToArraySize()
        unloadChunksIfPossible(camera.topLeftWorldPosition, camera.bottomRightWorldPosition)
    }

    private fun calculateArrayCoordinates(topLeft: Vector2f, bottomRight: Vector2f)
    {
        val xStartF = (topLeft.x / chunkSize) - xOffsetIndex - activeOfScreenChunkBorder
        val yStartF = (topLeft.y / chunkSize) - yOffsetIndex - activeOfScreenChunkBorder
        val xEndF = (bottomRight.x  / chunkSize) - xOffsetIndex + activeOfScreenChunkBorder
        val yEndF = (bottomRight.y  / chunkSize) - yOffsetIndex + activeOfScreenChunkBorder

        xStart = if (xStartF < 0f) floor(xStartF).toInt() else xStartF.toInt()
        yStart = if (yStartF < 0f) floor(yStartF).toInt() else yStartF.toInt()
        xEnd = if (xEndF >= 0f) ceil(xEndF).toInt() else xEndF.toInt()
        yEnd = if (yEndF >= 0f) ceil(yEndF).toInt() else yEndF.toInt()
    }

    private fun calculateArrayOffset(topLeft: Vector2f, bottomRight: Vector2f)
    {
        val closeToLeftSide = xStart < 0
        val closeToRightSide = xEnd > xChunkCount
        var closeToTop = yStart < 0
        var closeToBottom = yEnd > yChunkCount

        if ((closeToLeftSide && closeToRightSide) || (closeToTop && closeToBottom))
        {
            growArray()
            calculateArrayCoordinates(topLeft, bottomRight)
        }
        else
        {
            if (closeToLeftSide || closeToRightSide)
            {
                val didRecenter = recenterXAxis()
                if (!didRecenter)
                    growArray()
                calculateArrayCoordinates(topLeft, bottomRight)
            }

            closeToTop = yStart < 0
            closeToBottom = yEnd > yChunkCount

            if (closeToTop || closeToBottom)
            {
                val didRecenter = recenterYAxis()
                if (!didRecenter)
                    growArray()
                calculateArrayCoordinates(topLeft, bottomRight)
            }
        }
    }

    private fun clampArrayCoordinatesToArraySize()
    {
        xStart = xStart.coerceIn(0, xChunkCount)
        yStart = yStart.coerceIn(0, yChunkCount)
        xEnd = xEnd.coerceIn(0, xChunkCount)
        yEnd = yEnd.coerceIn(0, yChunkCount)
    }

    private fun unloadChunksIfPossible(topLeft: Vector2f, bottomRight: Vector2f)
    {
        if (System.currentTimeMillis() - lastRecalculateTime > recalculateArraySizeInterval)
        {
            val width = xEnd - xStart
            val height = yEnd - yStart
            val newWidth = width + 2 * minSurroundingLoadedChunkBorder
            val newHeight = height + 2 * minSurroundingLoadedChunkBorder

            if (newWidth < xChunkCount * 0.80f || newHeight < yChunkCount * 0.80f)
            {
                shrinkArray(newWidth, newHeight)
                calculateArrayCoordinates(topLeft, bottomRight)
                calculateArrayOffset(topLeft, bottomRight)
                clampArrayCoordinatesToArraySize()
            }

            lastRecalculateTime = System.currentTimeMillis()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun growArray()
    {
        val newWidth = (xEnd - xStart) + 2 * minSurroundingLoadedChunkBorder
        val newHeight = (yEnd - yStart) + 2 * minSurroundingLoadedChunkBorder
        val xSizeDiff = newWidth - xChunkCount
        val ySizeDiff = newHeight - yChunkCount
        val xNewOffsetIndex = xOffsetIndex - xSizeDiff / 2
        val yNewOffsetIndex = yOffsetIndex - ySizeDiff / 2
        val xOffsetDiff = xOffsetIndex - xNewOffsetIndex
        val yOffsetDiff = yOffsetIndex - yNewOffsetIndex

        val newLoadedChunks =
            IntRange(0, newHeight).map { y ->
                IntRange(0, newWidth).map { x ->
                    if(x >= xOffsetDiff && y >= yOffsetDiff && x < xOffsetDiff + xChunkCount && y < yOffsetDiff + yChunkCount)
                        loadedChunks[y - yOffsetDiff][x - xOffsetDiff]
                    else
                        onChunkLoadCallback.invoke(x + xNewOffsetIndex, y + yNewOffsetIndex)
                }.toTypedArray()
            }.toTypedArray()

        xChunkCount = newWidth
        yChunkCount = newHeight
        xOffsetIndex = xNewOffsetIndex
        yOffsetIndex = yNewOffsetIndex
        loadedChunks = newLoadedChunks
    }

    @Suppress("UNCHECKED_CAST")
    private fun shrinkArray(newWidth: Int, newHeight: Int)
    {
        val xNewOffsetIndex = xOffsetIndex + (xStart + xEnd) / 2 - newWidth / 2
        val yNewOffsetIndex = yOffsetIndex + (yStart + yEnd) / 2 - newHeight / 2
        val xDiff = xNewOffsetIndex - xOffsetIndex
        val yDiff = yNewOffsetIndex - yOffsetIndex

        val newLoadedChunks =
            IntRange(0, newHeight).map { y ->
                IntRange(0, newWidth).map { x ->
                    val xOld = x + xDiff
                    val yOld = y + yDiff
                    if(xOld >= 0 && xOld < xChunkCount && yOld >= 0 && yOld < yChunkCount)
                        loadedChunks[yOld][xOld]
                    else
                        onChunkLoadCallback.invoke(x + xNewOffsetIndex, y + yNewOffsetIndex)
                }.toTypedArray()
            }.toTypedArray()

        // Save all chunks outside off new array
        for (y in 0 until yChunkCount)
            for (x in 0 until xChunkCount)
                if (x < xDiff || y < yDiff || x > xDiff + newWidth || y > yDiff + newHeight)
                    saveChunk(loadedChunks[y][x] as T, x + xOffsetIndex, y + yOffsetIndex)

        xStart -= xDiff
        yStart -= yDiff
        xEnd -= xDiff
        yEnd -= yDiff
        xChunkCount = newWidth
        yChunkCount = newHeight
        xOffsetIndex = xNewOffsetIndex
        yOffsetIndex = yNewOffsetIndex
        loadedChunks = newLoadedChunks
    }

    @Suppress("UNCHECKED_CAST")
    private fun recenterXAxis(): Boolean
    {
        val xNewOffsetIndex = xOffsetIndex + (xEnd + xStart) / 2 - xChunkCount / 2
        val xDiff = xNewOffsetIndex - xOffsetIndex

        if (xDiff == 0)
            return false

        if (xDiff > 0)
        {
            for (y in 0 until yChunkCount)
            {
                // Save chunks outside of array on left side
                for (x in 0 until xDiff)
                    saveChunk(loadedChunks[y][x] as T, x + xOffsetIndex, y + yOffsetIndex)

                // Move data to the left in array and load chunks for new right side region
                for (x in 0 until xChunkCount)
                {
                    if(x + xDiff < xChunkCount)
                        loadedChunks[y][x] = loadedChunks[y][x+xDiff]
                    else
                        loadedChunks[y][x] = onChunkLoadCallback.invoke(x + xOffsetIndex + xDiff, y + yOffsetIndex)
                }
            }
        }
        else
        {
            for (y in 0 until yChunkCount)
            {
                // Save chunks outside of array on right side
                for (x in xChunkCount + xDiff until xChunkCount)
                    saveChunk(loadedChunks[y][x] as T, x + xOffsetIndex, y + yOffsetIndex)

                // Move data to the right in array and load chunks for new left side region
                for (x in (xChunkCount - 1) downTo 0)
                {
                    if(x + xDiff >= 0)
                        loadedChunks[y][x] = loadedChunks[y][x + xDiff]
                    else
                        loadedChunks[y][x] = onChunkLoadCallback.invoke(x + xOffsetIndex + xDiff, y + yOffsetIndex)
                }
            }
        }

        xOffsetIndex = xNewOffsetIndex
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun recenterYAxis(): Boolean
    {
        val yNewOffsetIndex = yOffsetIndex + (yEnd + yStart) / 2 - yChunkCount / 2
        val yDiff = yNewOffsetIndex - yOffsetIndex

        if(yDiff == 0)
            return false

        if (yDiff > 0)
        {
            // Save chunks outside of array on top side
            for (y in 0 until yDiff)
                for (x in 0 until xChunkCount)
                    saveChunk(loadedChunks[y][x] as T, x + xOffsetIndex, y + yOffsetIndex)

            for (y in 0 until yChunkCount)
            {
                if(y + yDiff < yChunkCount)
                {
                    // Move data upward in array
                    for (x in 0 until xChunkCount)
                        loadedChunks[y][x] = loadedChunks[y + yDiff][x]
                }
                else
                {
                    // Load chunks for new lower region
                    for (x in 0 until xChunkCount)
                        loadedChunks[y][x] = onChunkLoadCallback.invoke(x + xOffsetIndex, y + yOffsetIndex + yDiff)
                }
            }
        }
        else
        {
            // Save chunks outside of array on bottom side
            for (y in yChunkCount + yDiff until yChunkCount)
                for (x in 0 until xChunkCount)
                    saveChunk(loadedChunks[y][x] as T, x + xOffsetIndex, y + yOffsetIndex)

            for (y in (yChunkCount - 1) downTo  0)
            {
                if (y + yDiff >= 0)
                {
                    // Move data downward in array
                    for (x in 0 until xChunkCount)
                        loadedChunks[y][x] = loadedChunks[y+yDiff][x]
                }
                else
                {
                    // Load chunks for new upper region
                    for (x in 0 until xChunkCount)
                        loadedChunks[y][x] = onChunkLoadCallback.invoke(x + xOffsetIndex, y + yOffsetIndex + yDiff)
                }
            }
        }

        yOffsetIndex = yNewOffsetIndex
        return true
    }

    private fun saveChunk(chunk: T, xIndex: Int, yIndedx: Int)
    {
        if (chunk.hasData())
            onChunkSaveCallback.invoke(chunk, xIndex, yIndedx)
    }

    fun renderDebug(gfx: GraphicsInterface)
    {
        if (!this::debugSurface.isInitialized)
            debugSurface = gfx.createSurface2D("cmDebugSurface")

        for(chunk in getActiveChunks())
        {
            val x = chunk.x * chunkSize.toFloat()
            val y = chunk.y * chunkSize.toFloat()

            gfx.mainSurface.setDrawColor(0f, 1f, 0f, 0.5f)
            gfx.mainSurface.drawLine(x, y, x + chunkSize, y)
            gfx.mainSurface.drawLine(x + chunkSize, y, x + chunkSize, y + chunkSize)
            gfx.mainSurface.drawLine(x + chunkSize, y + chunkSize, x, y + chunkSize)
            gfx.mainSurface.drawLine(x, y + chunkSize, x, y)
            gfx.mainSurface.setDrawColor(1f, 1f, 1f)
            gfx.mainSurface.drawText("(${chunk.x},${chunk.y})", x + chunkSize / 2, y + chunkSize / 2f, xOrigin = 0.5f, yOrigin = 0.5f, fontSize = 72f)
        }

        for (yi in 0 until yChunkCount)
        {
            for (xi in 0 until xChunkCount)
            {
                val size = 10f
                val x = 10 + xi * size
                val y = 10 + yi * size

                debugSurface.setDrawColor(0.6f, 0.6f, 0.6f, 0.9f)
                debugSurface.drawLine(x, y, x + size, y)
                debugSurface.drawLine(x + size, y, x + size, y + size)
                debugSurface.drawLine(x + size, y + size, x, y + size)
                debugSurface.drawLine(x, y + size, x, y)

                if (xi >= xStart && xi < xEnd && yi >= yStart && yi < yEnd)
                {
                    val border = activeOfScreenChunkBorder

                    if (xi >= xStart + border && xi < xEnd - border && yi >= yStart + border && yi < yEnd - border)
                        debugSurface.setDrawColor(0f, 1f, 0f, 0.9f)
                    else
                        debugSurface.setDrawColor(1f, 1f, 0f, 0.5f)

                    debugSurface.drawQuad(x, y, size, size)
                }
            }
        }

        debugSurface.setDrawColor(1f, 1f, 1f, 1f)
        debugSurface.drawText("x: $xOffsetIndex", 10f, 30f + 10 * yChunkCount )
        debugSurface.drawText("y: $yOffsetIndex", 10f, 50f + 10 * yChunkCount )
        debugSurface.drawText("Loaded: ${getLoadedChunkCount()}", 10f, 70f + 10 * yChunkCount )
        debugSurface.drawText("Active: ${getActiveChunkCount()}", 10f, 90f + 10 * yChunkCount )
        debugSurface.drawText("Visible: ${getVisibleChunkCount()}", 10f, 110f + 10 * yChunkCount )
        debugSurface.drawText("Border: $activeOfScreenChunkBorder", 10f, 130f + 10 * yChunkCount )
    }

    fun getLoadedChunkCount() =
        xChunkCount * yChunkCount

    fun getActiveChunkCount() =
        (xEnd - xStart) * (yEnd - yStart)

    fun getVisibleChunkCount() =
        (xEnd - xStart - 2 * activeOfScreenChunkBorder) * (yEnd - yStart - 2 * activeOfScreenChunkBorder)

    fun getLoadedChunks() =
        iterator.also { it.reset(0, 0, xChunkCount, yChunkCount) }

    fun getActiveChunks() =
        iterator.also { it.reset(xStart, yStart, xEnd, yEnd) }

    fun getVisibleChunks() =
        iterator.also {
            val border = activeOfScreenChunkBorder
            it.reset(xStart + border, yStart + border, xEnd + border, yEnd + border)
        }

    inner class ChunkIterator : Iterator<T>
    {
        private var x = 0
        private var y = 0

        private var xIteratorStart = 0
        private var yIteratorStart = 0
        private var xIteratorEnd = 0
        private var yIteratorEnd = 0

        fun reset(xStart: Int, yStart: Int, xEnd: Int, yEnd: Int)
        {
            xIteratorStart = xStart
            yIteratorStart = yStart
            xIteratorEnd = xEnd
            yIteratorEnd = yEnd
            x = xIteratorStart
            y = yIteratorStart
        }

        override fun hasNext(): Boolean =
            y < yIteratorEnd

        @Suppress("UNCHECKED_CAST")
        override fun next(): T
        {
            val next = loadedChunks[y][x]
            if (++x >= xIteratorEnd)
            {
                x = xIteratorStart
                y++
            }
            return next as T
        }
    }
}

interface Chunk
{
    val x: Int
    val y: Int
    fun hasData(): Boolean
}
