package no.njoh.pulseengine.core.graphics.util

import it.unimi.dsi.fastutil.ints.IntArrayList
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.shared.datastructures.DynamicList
import org.lwjgl.opengl.GL33.*

/**
 * Utility class for measuring the time between two specific points in the GPU pipeline.
 */
class GpuTimeQuery
{
    private var timerId      = 0L
    private var startQueryId = -1
    private var endQueryId   = -1
    private var depth        = 0
    private var label        = StringBuilder(100)
    private var frameNumber  = 0L

    private fun start(timerId: Long, label: CharSequence, depth: Int)
    {
        if (depth == 0) currentFrameNumber++

        this.timerId = timerId
        this.startQueryId = getQueryId()
        this.depth = depth
        this.frameNumber = currentFrameNumber
        this.label.clear().append(label)
        glQueryCounter(startQueryId, GL_TIMESTAMP)
    }

    private fun end()
    {
        endQueryId = getQueryId()
        glQueryCounter(endQueryId, GL_TIMESTAMP)
    }

    private fun isResultReady(): Boolean
    {
        val hasResult = glGetQueryObjectui(endQueryId, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE
        return hasResult
    }

    private fun getResult(): GpuTimeQueryResult
    {
        val startTime = glGetQueryObjectui64(startQueryId, GL_QUERY_RESULT)
        val endTime   = glGetQueryObjectui64(endQueryId,   GL_QUERY_RESULT)
        val result    = resultPool.removeLastOrNull() ?: GpuTimeQueryResult()
        
        result.label.clear().append(label)
        result.timerId     = timerId
        result.depth       = depth
        result.timeNanoSec = endTime - startTime
        result.frameNumber = frameNumber

        return result
    }

    private fun recycle()
    {
        queryIdPool.add(startQueryId)
        queryIdPool.add(endQueryId)
        startQueryId = -1
        endQueryId = -1
        timerPool += this
    }

    private fun reset()
    {
        timerId = 0L
        startQueryId = -1
        endQueryId = -1
        depth = 0
        label.clear()
    }

    companion object
    {
        private val queryIdPool  = IntArrayList(1000)
        private val timerPool    = DynamicList<GpuTimeQuery>()
        private val resultPool   = DynamicList<GpuTimeQueryResult>()
        private val timerStack   = ArrayDeque<GpuTimeQuery>()
        private val activeTimers = ArrayDeque<GpuTimeQuery>()
        private val readyResults = DynamicList<GpuTimeQueryResult>()
        private var contextGeneration = -1L

        private var currentFrameNumber = 0L

        /**
         * Returns the most recently completed frame. Results are pooled and reused by the next poll.
         * Safe to read from the game thread between the engine's frame barriers.
         */
        fun getAllResults(): DynamicList<GpuTimeQueryResult> = readyResults

        /**
         * Polls the results of all ready timers and moves them to the ready results list.
         * Should only be called from the main/graphics thread at the beginning of the frame.
         */
        fun pollResults()
        {
            ensureContextGeneration()

            while (activeTimers.isNotEmpty())
            {
                val root = activeTimers.first()
                if (root.endQueryId < 0 || !root.isResultReady()) 
                    return

                resultPool += readyResults
                readyResults.clear()

                do
                {
                    val timer = activeTimers.removeFirst()
                    readyResults += timer.getResult()
                    timer.recycle()
                }
                while (activeTimers.isNotEmpty() && activeTimers.first().depth != 0)
            }
        }

        /**
         * Starts a new timer with the given ID and label.
         */
        fun start(timerId: Long, label: CharSequence)
        {
            ensureContextGeneration()
            val timer = timerPool.removeLastOrNull() ?: GpuTimeQuery()
            timer.start(timerId, label, depth = timerStack.size)
            timerStack += timer
            activeTimers += timer
        }

        /**
         * Ends the last started timer.
         */
        fun end()
        {
            ensureContextGeneration()
            timerStack.removeLastOrNull()?.end()
        }

        internal fun onContextRecreated()
        {
            val currentGeneration = GlCapabilities.contextGeneration
            if (contextGeneration == currentGeneration) return

            contextGeneration = currentGeneration
            queryIdPool.clear()
            timerStack.clear()

            while (activeTimers.isNotEmpty())
            {
                val timer = activeTimers.removeFirst()
                timer.reset()
                timerPool += timer
            }

            resultPool += readyResults
            readyResults.clear()
        }

        private fun getQueryId() = if (queryIdPool.isEmpty) glGenQueries() else queryIdPool.removeInt(queryIdPool.size - 1)

        private fun ensureContextGeneration()
        {
            if (contextGeneration != GlCapabilities.contextGeneration)
                onContextRecreated()
        }
    }
}

data class GpuTimeQueryResult(
    var label: StringBuilder = StringBuilder(50),
    var depth: Int = -1,
    var timeNanoSec: Long = -1L,
    var timerId: Long = 0L,
    var frameNumber: Long = 0L
)