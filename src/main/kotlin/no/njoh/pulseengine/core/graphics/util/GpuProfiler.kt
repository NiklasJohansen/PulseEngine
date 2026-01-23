package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.shared.utils.TextBuilderContext
import no.njoh.pulseengine.core.shared.utils.TextBuilder

/**
 * Utility class for asynchronously measuring GPU timings.
 */
object GpuProfiler
{
    @PublishedApi internal var enabled = false; private set
    @PublishedApi internal val context = TextBuilderContext()

    private var shouldBeEnabled = false
    private var drawCallCounter = 0L
    private var triangleCounter = 0L

    var drawCalls = 0L; private set
    var triangles = 0L; private set

    /**
     * Measures the time it takes to execute the given [action].
     * Only call this on the graphics thread.
     */
    inline fun <T> measure(label: TextBuilder, action: () -> T): T
    {
        beginMeasure(label)
        return try { action() } finally { endMeasure() }
    }

    inline fun <T> measure(label: String, action: () -> T) = measure({ label }, action)

    /**
     * Begins a GPU time measure.
     * Only call this on the graphics thread.
     */
    inline fun beginMeasure(label: TextBuilder)
    {
        if (!enabled) return

        val labelText = context.build(label)
        GpuTimeQuery.start(labelText)
        GpuLogger.beginGroup(labelText)
    }

    /**
     * Ends the current GPU time measure.
     * Only call this on the graphics thread.
     */
    fun endMeasure()
    {
        if (!enabled) return

        GpuLogger.endGroup()
        GpuTimeQuery.end()
    }

    /**
     * Returns all time measurements that have been collected from the previous frame.
     * Safe to call from the game thread.
     */
    fun getMeasurements() = GpuTimeQuery.getAllResults()

    /**
     * Enables/disables the GPU Profiler.
     * Safe to call from the game thread as the state is changes at the beginning of the next frame.
     */
    fun setEnabled(enabled: Boolean)
    {
        shouldBeEnabled = enabled
    }

    /**
     * Starts measuring the frame time and polls the results from the previous frame.
     * Called by the engine form the graphics thread.
     */
    internal fun initFrame()
    {
        enabled = shouldBeEnabled
        drawCalls = drawCallCounter
        triangles = triangleCounter
        drawCallCounter = 0
        triangleCounter = 0

        if (!enabled) return

        GpuTimeQuery.pollResults()
        GpuTimeQuery.start("FRAME")
    }

    /**
     * Ends the frame time measure.
     * Called by the engine form the graphics thread.
     */
    internal fun endFrame()
    {
        if (!enabled) return

        GpuTimeQuery.end() // End the "Frame" timer
    }

    /**
     * Increments the draw call counter by the given [count].
     */
    fun incrementDrawCalls(count: Long = 1L)
    {
        drawCallCounter += count
    }

    /**
     * Increments the triangle counter by the given [count].
     */
    fun incrementTriangles(count: Long)
    {
        triangleCounter += count
    }
}