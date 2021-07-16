package no.njoh.pulseengine.util

import no.njoh.pulseengine.data.Array2D
import no.njoh.pulseengine.modules.graphics.CameraInterface
import no.njoh.pulseengine.modules.graphics.Graphics
import no.njoh.pulseengine.modules.graphics.Surface2D
import org.joml.Vector2f
import java.lang.IllegalStateException
import kotlin.math.ceil
import kotlin.math.floor

class ChunkManager <T: Chunk> (
    val chunkSize: Int = 1000,
    private val minSurroundingLoadedChunkBorder: Int = 5,
    private val activeOfScreenChunkBorder: Int = 1
) {
    private lateinit var onChunkLoadCallback: (x: Int, y: Int) -> T
    private lateinit var onChunkSaveCallback: (chunk: T, x: Int, y: Int) -> Unit
    private lateinit var loadedChunks: Array2D<T>
    private lateinit var debugSurface: Surface2D

    private var xOffsetIndex = 0
    private var yOffsetIndex = 0
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

    fun init()
    {
        if (!this::onChunkLoadCallback.isInitialized)
            throw IllegalStateException("onChunkLoad callback has not been set for ChunkManager")

        if (!this::onChunkSaveCallback.isInitialized)
            throw IllegalStateException("onChunkSave callback has not been set for ChunkManager")

        val width = 5 + minSurroundingLoadedChunkBorder * 2
        val height = 5 + minSurroundingLoadedChunkBorder * 2
        xOffsetIndex = -width / 2
        yOffsetIndex = -height / 2
        loadedChunks = Array2D(width, height) { x, y -> onChunkLoadCallback.invoke(x + xOffsetIndex, y + yOffsetIndex) }
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
        val closeToRightSide = xEnd > loadedChunks.width
        var closeToTop = yStart < 0
        var closeToBottom = yEnd > loadedChunks.height

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
            closeToBottom = yEnd > loadedChunks.height

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
        xStart = xStart.coerceIn(0, loadedChunks.width)
        yStart = yStart.coerceIn(0, loadedChunks.height)
        xEnd = xEnd.coerceIn(0, loadedChunks.width)
        yEnd = yEnd.coerceIn(0, loadedChunks.height)
    }

    private fun unloadChunksIfPossible(topLeft: Vector2f, bottomRight: Vector2f)
    {
        if (System.currentTimeMillis() - lastRecalculateTime > recalculateArraySizeInterval)
        {
            val width = xEnd - xStart
            val height = yEnd - yStart
            val newWidth = width + 2 * minSurroundingLoadedChunkBorder
            val newHeight = height + 2 * minSurroundingLoadedChunkBorder

            if (newWidth < loadedChunks.width * 0.80f || newHeight < loadedChunks.height * 0.80f)
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
        val xSizeDiff = newWidth - loadedChunks.width
        val ySizeDiff = newHeight - loadedChunks.height
        val xNewOffsetIndex = xOffsetIndex - xSizeDiff / 2
        val yNewOffsetIndex = yOffsetIndex - ySizeDiff / 2
        val xOffsetDiff = xOffsetIndex - xNewOffsetIndex
        val yOffsetDiff = yOffsetIndex - yNewOffsetIndex

        xOffsetIndex = xNewOffsetIndex
        yOffsetIndex = yNewOffsetIndex
        loadedChunks = Array2D(newWidth, newHeight) { x, y ->
            if (x >= xOffsetDiff && y >= yOffsetDiff && x < xOffsetDiff + loadedChunks.width && y < yOffsetDiff + loadedChunks.height)
                loadedChunks[x - xOffsetDiff, y - yOffsetDiff]
            else
                onChunkLoadCallback.invoke(x + xNewOffsetIndex, y + yNewOffsetIndex)
        }
    }

    private fun shrinkArray(newWidth: Int, newHeight: Int)
    {
        val xNewOffsetIndex = xOffsetIndex + (xStart + xEnd) / 2 - newWidth / 2
        val yNewOffsetIndex = yOffsetIndex + (yStart + yEnd) / 2 - newHeight / 2
        val xDiff = xNewOffsetIndex - xOffsetIndex
        val yDiff = yNewOffsetIndex - yOffsetIndex

        val newLoadedChunks = Array2D(newWidth, newHeight) { x, y ->
            val xOld = x + xDiff
            val yOld = y + yDiff
            if (xOld >= 0 && xOld < loadedChunks.width && yOld >= 0 && yOld < loadedChunks.height)
                loadedChunks[xOld, yOld]
            else
                onChunkLoadCallback.invoke(x + xNewOffsetIndex, y + yNewOffsetIndex)
        }

        for (y in 0 until loadedChunks.height)
            for (x in 0 until loadedChunks.width)
                if (x < xDiff || y < yDiff || x > xDiff + newWidth || y > yDiff + newHeight)
                    saveChunk(loadedChunks[x, y], x + xOffsetIndex, y + yOffsetIndex)

        xStart -= xDiff
        yStart -= yDiff
        xEnd -= xDiff
        yEnd -= yDiff
        xOffsetIndex = xNewOffsetIndex
        yOffsetIndex = yNewOffsetIndex
        loadedChunks = newLoadedChunks
    }

    private fun recenterXAxis(): Boolean
    {
        val xNewOffsetIndex = xOffsetIndex + (xEnd + xStart) / 2 - loadedChunks.width / 2
        val xDiff = xNewOffsetIndex - xOffsetIndex

        if (xDiff == 0)
            return false

        if (xDiff > 0)
        {
            for (y in 0 until loadedChunks.height)
            {
                // Save chunks outside of array on left side
                for (x in 0 until xDiff)
                    saveChunk(loadedChunks[x, y], x + xOffsetIndex, y + yOffsetIndex)

                // Move data to the left in array and load chunks for new right side region
                for (x in 0 until loadedChunks.width)
                {
                    if (x + xDiff < loadedChunks.width)
                        loadedChunks[x, y] = loadedChunks[x + xDiff, y]
                    else
                        loadedChunks[x, y] = onChunkLoadCallback.invoke(x + xOffsetIndex + xDiff, y + yOffsetIndex)
                }
            }
        }
        else
        {
            for (y in 0 until loadedChunks.height)
            {
                // Save chunks outside of array on right side
                for (x in loadedChunks.width + xDiff until loadedChunks.width)
                    saveChunk(loadedChunks[x, y], x + xOffsetIndex, y + yOffsetIndex)

                // Move data to the right in array and load chunks for new left side region
                for (x in (loadedChunks.width - 1) downTo 0)
                {
                    if (x + xDiff >= 0)
                        loadedChunks[x, y] = loadedChunks[x + xDiff, y]
                    else
                        loadedChunks[x, y] = onChunkLoadCallback.invoke(x + xOffsetIndex + xDiff, y + yOffsetIndex)
                }
            }
        }

        xOffsetIndex = xNewOffsetIndex
        return true
    }

    private fun recenterYAxis(): Boolean
    {
        val yNewOffsetIndex = yOffsetIndex + (yEnd + yStart) / 2 - loadedChunks.height / 2
        val yDiff = yNewOffsetIndex - yOffsetIndex

        if (yDiff == 0)
            return false

        if (yDiff > 0)
        {
            // Save chunks outside of array on top side
            for (y in 0 until yDiff)
                for (x in 0 until loadedChunks.width)
                    saveChunk(loadedChunks[x, y], x + xOffsetIndex, y + yOffsetIndex)

            for (y in 0 until loadedChunks.height)
            {
                if (y + yDiff < loadedChunks.height)
                {
                    // Move data upward in array
                    for (x in 0 until loadedChunks.width)
                        loadedChunks[x, y] = loadedChunks[x, y + yDiff]
                }
                else
                {
                    // Load chunks for new lower region
                    for (x in 0 until loadedChunks.width)
                        loadedChunks[x, y] = onChunkLoadCallback.invoke(x + xOffsetIndex, y + yOffsetIndex + yDiff)
                }
            }
        }
        else
        {
            // Save chunks outside of array on bottom side
            for (y in loadedChunks.height + yDiff until loadedChunks.height)
                for (x in 0 until loadedChunks.width)
                    saveChunk(loadedChunks[x, y], x + xOffsetIndex, y + yOffsetIndex)

            for (y in (loadedChunks.height - 1) downTo  0)
            {
                if (y + yDiff >= 0)
                {
                    // Move data downward in array
                    for (x in 0 until loadedChunks.width)
                        loadedChunks[x, y] = loadedChunks[x, y + yDiff]
                }
                else
                {
                    // Load chunks for new upper region
                    for (x in 0 until loadedChunks.width)
                        loadedChunks[x, y] = onChunkLoadCallback.invoke(x + xOffsetIndex, y + yOffsetIndex + yDiff)
                }
            }
        }

        yOffsetIndex = yNewOffsetIndex
        return true
    }

    private fun saveChunk(chunk: T, xIndex: Int, yIndex: Int)
    {
        if (chunk.hasData())
            onChunkSaveCallback.invoke(chunk, xIndex, yIndex)
    }

    fun renderDebug(gfx: Graphics)
    {
        if (!this::debugSurface.isInitialized)
            debugSurface = gfx.createSurface("cmDebugSurface")

        for (chunk in getActiveChunks())
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

        for (yi in 0 until loadedChunks.height)
        {
            for (xi in 0 until loadedChunks.width)
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
        debugSurface.drawText("x: $xOffsetIndex", 10f, 30f + 10 * loadedChunks.height )
        debugSurface.drawText("y: $yOffsetIndex", 10f, 50f + 10 * loadedChunks.height )
        debugSurface.drawText("Loaded: ${getLoadedChunkCount()}", 10f, 70f + 10 * loadedChunks.height )
        debugSurface.drawText("Active: ${getActiveChunkCount()}", 10f, 90f + 10 * loadedChunks.height )
        debugSurface.drawText("Visible: ${getVisibleChunkCount()}", 10f, 110f + 10 * loadedChunks.height )
        debugSurface.drawText("Border: $activeOfScreenChunkBorder", 10f, 130f + 10 * loadedChunks.height )
    }

    fun getLoadedChunkCount() =
        loadedChunks.width * loadedChunks.height

    fun getActiveChunkCount() =
        (xEnd - xStart) * (yEnd - yStart)

    fun getVisibleChunkCount() =
        (xEnd - xStart - 2 * activeOfScreenChunkBorder) * (yEnd - yStart - 2 * activeOfScreenChunkBorder)

    fun getLoadedChunks() =
        iterator.also { it.reset(0, 0, loadedChunks.width, loadedChunks.height) }

    fun getActiveChunks() =
        iterator.also { it.reset(xStart, yStart, xEnd, yEnd) }

    fun getVisibleChunks() =
        iterator.also {
            val border = activeOfScreenChunkBorder
            it.reset(xStart + border, yStart + border, xEnd - border, yEnd - border)
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

        override fun next(): T
        {
            val next = loadedChunks[x, y]
            if (++x >= xIteratorEnd)
            {
                x = xIteratorStart
                y++
            }
            return next
        }
    }
}

interface Chunk
{
    val x: Int
    val y: Int
    fun hasData(): Boolean
}
