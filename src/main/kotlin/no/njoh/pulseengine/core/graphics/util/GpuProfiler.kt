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
    private var instanceCounter = 0L
    private var scene3DInstanceCounter = 0L
    private var uploadedBytesCounter = 0L
    private var statsReader = null as GpuStatsReader?

    var gpuTimeNs = 0L;        private set
    var drawCalls = 0L;        private set
    var triangles = 0L;        private set
    var instances = 0L;        private set
    var scene3DInstances = 0L; private set
    var uploadedBytes  = 0L;   private set

    /**
     * Measures an operation whose display [label] may change while [id] remains stable.
     */
    inline fun <T> measure(id: CharSequence, label: TextBuilder, action: () -> T): T
    {
        beginMeasure(id, label)
        return try { action() } finally { endMeasure() }
    }

    /**
     * Measures an operation using [id] as both its stable identity and display label.
     */
    inline fun <T> measure(id: CharSequence, action: () -> T): T
    {
        beginMeasure(id)
        return try { action() } finally { endMeasure() }
    }

    /**
     * Begins a GPU time measure using [id] as both its stable identity and display label.
     * Only call this on the graphics thread.
     */
    fun beginMeasure(id: CharSequence)
    {
        if (!enabled) return

        GpuTimeQuery.start(hashTimerId(id), id)
        GpuLogger.beginGroup(id)
    }

    /**
     * Begins a GPU time measure whose display [label] may change while [id] remains stable.
     * Only call this on the graphics thread.
     */
    inline fun beginMeasure(id: CharSequence, label: TextBuilder)
    {
        if (!enabled) return

        val labelText = context.build(label)
        GpuTimeQuery.start(hashTimerId(id), labelText)
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
        statsReader?.pollResults()
        enabled = shouldBeEnabled
        drawCalls = drawCallCounter
        triangles = triangleCounter
        instances = instanceCounter
        scene3DInstances = scene3DInstanceCounter
        uploadedBytes = uploadedBytesCounter
        drawCallCounter = 0
        triangleCounter = 0
        instanceCounter = 0
        scene3DInstanceCounter = 0
        uploadedBytesCounter = 0

        if (!enabled) return

        GpuTimeQuery.pollResults()
        GpuTimeQuery.start(hashTimerId("Frame"), "Frame")
    }

    /**
     * Ends the frame time measure.
     * Called by the engine form the graphics thread.
     */
    internal fun endFrame()
    {
        if (!enabled) return

        GpuTimeQuery.end() // End the "Frame" timer
        getMeasurements()
            .firstOrNull { it.depth == 0 }
            ?.let { gpuTimeNs = it.timeNanoSec }
    }

    internal fun onContextRecreated()
    {
        GpuTimeQuery.onContextRecreated()
        statsReader?.destroy()
        statsReader = null
        gpuTimeNs = 0L
    }

    internal fun destroy()
    {
        statsReader?.destroy()
        statsReader = null
    }

    /**
     * Increments all draw work counters.
     */
    fun incrementDrawStats(drawCommands: Long, triangles: Long = 0L, instances: Long = 0L)
    {
        drawCallCounter += drawCommands
        triangleCounter += triangles
        instanceCounter += instances
    }

    /**
     * Increments the number of 3D scene instances rendered this frame.
     */
    fun incrementScene3DInstances(count: Long)
    {
        scene3DInstanceCounter += count
    }

    /**
     * Increments the number of bytes uploaded from CPU to GPU buffers this frame.
     */
    fun incrementUploadedBytes(count: Long)
    {
        uploadedBytesCounter += count
    }

    /**
     * Asynchronously captures visible draw stats from a GPU-written indirect command buffer.
     */
    fun captureIndirectDrawStats(commandBufferId: Int, byteOffset: Long, commandCount: Int)
    {
        if (commandBufferId <= 0 || commandCount <= 0) return

        val reader = statsReader ?: GpuStatsReader().also { statsReader = it }

        reader.capture(commandBufferId, byteOffset, commandCount)
    }

    /**
     * Generates a stable primitive ID.
     */
    @PublishedApi
    internal fun hashTimerId(id: CharSequence): Long
    {
        var hash = -3750763034362895579L
        for (i in 0 until id.length)
            hash = (hash xor id[i].code.toLong()) * 1099511628211L
        return hash
    }
}