package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.shared.utils.TextBuilderContext
import no.njoh.pulseengine.core.shared.utils.TextBuilder

/**
 * Utility class for asynchronously measuring GPU timings.
 */
object GpuProfiler
{
    @PublishedApi internal var ENABLED = true
    @PublishedApi internal val context = TextBuilderContext()

    /**
     * Starts measuring the frame time and polls the results from the previous frame.
     * Called by the engine form the graphics thread.
     */
    internal fun startFrame()
    {
        if (!ENABLED) return

        GpuTimer.pollResults()
        GpuTimer.start(context.build { "Frame" })
    }

    /**
     * Ends the frame time measure.
     * Called by the engine form the graphics thread.
     */
    internal fun endFrame()
    {
        if (!ENABLED) return

        GpuTimer.end() // End the "Frame" timer
    }

    /**
     * Begins a GPU time measure.
     * Only call this on the graphics thread.
     */
    inline fun beginMeasure(label: TextBuilder)
    {
        if (!ENABLED) return

        val labelText = context.build(label)
        GpuTimer.start(labelText)
        GpuLogger.beginGroup(labelText)
    }

    /**
     * Ends the current GPU time measure.
     * Only call this on the graphics thread.
     */
    fun endMeasure()
    {
        if (!ENABLED) return

        GpuLogger.endGroup()
        GpuTimer.end()
    }

    /**
     * Measures the time it takes to execute the given [action].
     * Only call this on the graphics thread.
     */
    inline fun measure(label: TextBuilder, action: () -> Unit)
    {
        if (!ENABLED)
        {
            action()
            return
        }

        val text = context.build(label)
        GpuTimer.start(text)
        GpuLogger.beginGroup(text)

        action()

        GpuLogger.endGroup()
        GpuTimer.end()
    }

    /**
     * Returns all time measurements that have been collected from the previous frame.
     * Safe to call from the game thread.
     */
    fun getMeasurements() = GpuTimer.getAllResults()

    /**
     * Used to enable or disable GPU profiling.
     */
    fun setEnabled(state: Boolean) { ENABLED = state }
}