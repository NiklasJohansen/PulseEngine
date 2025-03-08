package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.util.GpuProfiler

/**
 * Used to batch up vertex data into separate draw calls.
 * Managed by the [Graphics] implementation.
 */
abstract class BatchRenderer
{
    private val batchSize  = IntArray(MAX_BATCH_COUNT * 2)
    private val batchStart = IntArray(MAX_BATCH_COUNT * 2)

    private var currentBatch = 0
    private var currentStart = 0
    private var currentSize  = 0
    private var readOffset   = 0
    private var writeOffset  = MAX_BATCH_COUNT
    private var hadContent   = false
    private var hasContent   = false
    private var wasUpdated   = false
    private var name         = this::class.java.simpleName

    /**
     * Called once at the beginning of every frame.
     */
    fun initFrame()
    {
        finishCurrentBatch()
        onInitFrame()

        readOffset = writeOffset.also { writeOffset = readOffset }
        hadContent = hasContent || wasUpdated
        hasContent = wasUpdated
        wasUpdated = false
        currentBatch = 0
        currentStart = 0
    }

    /**
     * Records the size of the current batch and prepares the next.
     */
    fun finishCurrentBatch()
    {
        val i = writeOffset + currentBatch
        batchSize[i] = currentSize
        batchStart[i] = currentStart
        currentStart += currentSize
        wasUpdated = (wasUpdated || currentSize > 0)
        currentSize = 0
        currentBatch++
    }

    /**
     * Called for every element that is added to the batch.
     */
    fun increaseBatchSize(amount: Int)
    {
        currentSize += amount
    }

    /**
     * Called for every element that is added to the batch.
     */
    fun increaseBatchSize()
    {
        currentSize++
    }

    /**
     * Renders the numbered batch if it is not empty.
     */
    fun renderBatch(engine: PulseEngineInternal, surface: Surface, batchNum: Int)
    {
        val i = readOffset + batchNum
        val drawCount = batchSize[i]
        if (drawCount == 0)
            return // Skip empty batches

        GpuProfiler.measure({ name plus " (" plus drawCount plus ")" })
        {
            onRenderBatch(engine, surface, batchStart[i], drawCount)
        }
    }

    /**
     * Checks if there are any batches to render. Will return true even if the current batch is
     * empty, but last one was not. This is done to ensure that surfaces are cleared properly
     * when there is no more content to render.
     */
    fun hasContentToRender() = hadContent

    /**
     * Called once when the renderer is added to the [Surface]
     */
    abstract fun init()

    /**
     * Called once at the start of every frame.
     */
    abstract fun onInitFrame()

    /**
     * Called every frame on every none-empty batch.
     */
    abstract fun onRenderBatch(engine: PulseEngineInternal, surface: Surface, startIndex: Int, drawCount: Int)

    /**
     * Called once when the [Surface] is destroyed.
     */
    abstract fun destroy()

    companion object
    {
        const val MAX_BATCH_COUNT = 64
    }
}