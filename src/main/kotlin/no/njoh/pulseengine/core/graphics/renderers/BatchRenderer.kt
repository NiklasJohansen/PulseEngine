package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.graphics.Surface2D

/**
 * Used to batch up vertex data into separate draw calls.
 * Handled by the [Graphics] implementation.
 */
abstract class BatchRenderer
{
    private var readOffset = 0
    private var writeOffset = MAX_BATCH_COUNT
    private var currentBatchNumber = 0
    private val batchSize = IntArray(MAX_BATCH_COUNT * 2)
    private val batchStartIndex = IntArray(MAX_BATCH_COUNT * 2)

    /**
     * Called once at the beginning of every frame.
     */
    fun initFrame()
    {
        readOffset = writeOffset.also { writeOffset = readOffset }
        onInitFrame()
    }

    /**
     * Sets the current bach number and the buffer start index.
     */
    fun setBatchNumber(number: Int)
    {
        currentBatchNumber = number
        var startIndex = 0
        for (num in 0 until number)
            startIndex += batchSize[num + writeOffset]
        batchStartIndex[number + writeOffset] = startIndex
    }

    /**
     * Called for every element that is added to the batch.
     */
    fun increaseBatchSize()
    {
        batchSize[currentBatchNumber + writeOffset]++
    }

    /**
     * Renders the numbered batch if it is not empty.
     */
    fun renderBatch(surface: Surface2D, batchNum: Int)
    {
        val drawCount = batchSize[batchNum + readOffset]
        if (drawCount == 0)
            return

        onRenderBatch(surface, batchStartIndex[batchNum + readOffset], drawCount)

        batchSize[batchNum + readOffset] = 0
    }

    /**
     * Called once when the renderer is added to the [Surface2D]
     */
    abstract fun init()

    /**
     * Called once at the start of every frame.
     */
    abstract fun onInitFrame()

    /**
     * Called every frame on every none-empty batch.
     */
    abstract fun onRenderBatch(surface: Surface2D, startIndex: Int, drawCount: Int)

    /**
     * Called once when the [Surface2D] is destroyed.
     */
    abstract fun cleanUp()

    companion object
    {
        const val MAX_BATCH_COUNT = 64
    }
}