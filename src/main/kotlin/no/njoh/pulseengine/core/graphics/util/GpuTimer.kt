package no.njoh.pulseengine.core.graphics.util

import gnu.trove.list.array.TIntArrayList
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL33.*

/**
 * Utility class for measuring the time between two specific points in the GPU pipeline.
 */
class GpuTimer
{
    private var label = StringBuilder(100)
    private var startQueryId = -1
    private var endQueryId = -1
    private var depth = 0

    private fun start(label: CharSequence, depth: Int)
    {
        this.label.clear().append(label)
        this.depth = depth
        this.startQueryId = getQueryId()
        glQueryCounter(startQueryId, GL_TIMESTAMP)
    }

    private fun end()
    {
        endQueryId = getQueryId()
        glQueryCounter(endQueryId, GL_TIMESTAMP)
    }

    private fun isResultReady(): Boolean = glGetQueryObjectui(endQueryId, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE

    private fun getResult(): GpuTimerResult
    {
        val startTime = glGetQueryObjectui64(startQueryId, GL_QUERY_RESULT)
        val endTime = glGetQueryObjectui64(endQueryId, GL_QUERY_RESULT)
        val result = resultPool.removeLastOrNull() ?: GpuTimerResult()
        result.label.clear().append(label)
        result.depth = depth
        result.timeNanoSec = endTime - startTime
        return result
    }

    private fun recycle()
    {
        queryIdPool.add(startQueryId)
        queryIdPool.add(endQueryId)
        startQueryId = -1
        endQueryId = -1
        timerPool.add(this)
    }

    companion object
    {
        private val queryIdPool  = TIntArrayList(1000)
        private val timerStack   = ArrayDeque<GpuTimer>()
        private val timerPool    = mutableListOf<GpuTimer>()
        private val resultPool   = mutableListOf<GpuTimerResult>()
        private val activeTimers = ArrayDeque<GpuTimer>()
        private val readyResults = mutableListOf<GpuTimerResult>()
        private val writeResults = mutableListOf<GpuTimerResult>()

        /**
         * Returns all ready results. Safe to call from game thread.
         */
        fun getAllResults(): List<GpuTimerResult> = readyResults

        /**
         * Polls the results of all ready timers and moves them to the ready results list.
         * Should only be called from the main/graphics thread at the beginning of the frame.
         */
        fun pollResults()
        {
            while (activeTimers.size > 0)
            {
                val timer = activeTimers.first()
                if (!timer.isResultReady()) break

                if (timer.depth == 0) // Frame timer
                {
                    resultPool.addAll(readyResults)
                    readyResults.clear()
                    readyResults += writeResults
                    writeResults.clear()
                }
                activeTimers.removeFirst()
                writeResults.add(timer.getResult())
                timer.recycle()
            }
        }

        /**
         * Starts a new timer with the given [label].
         */
        fun start(label: CharSequence)
        {
            if (activeTimers.size >= 200)
            {
                Logger.error("GpuTimer: Too many active timers! ${activeTimers.size}")
                return
            }

            val timer = timerPool.removeLastOrNull() ?: GpuTimer()
            timer.start(label, depth = timerStack.size)
            timerStack += timer
            activeTimers += timer
        }

        /**
         * Ends the last started timer.
         */
        fun end() = timerStack.removeLastOrNull()?.end()

        private fun getQueryId() = if (queryIdPool.isEmpty) glGenQueries() else queryIdPool.removeAt(queryIdPool.size() - 1)
    }
}

data class GpuTimerResult(
    var label: StringBuilder = StringBuilder(50),
    var depth: Int = -1,
    var timeNanoSec: Long = -1L
)