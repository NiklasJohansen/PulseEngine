package no.njoh.pulseengine.core.graphics.util

import gnu.trove.list.array.TIntArrayList
import org.lwjgl.opengl.GL33.*

/**
 * Utility class for measuring the time between two specific points in the GPU pipeline.
 */
class GpuTimeQuery
{
    private var label = StringBuilder(100)
    private var startQueryId = -1
    private var endQueryId = -1
    private var depth = 0
    private var framesWithoutResult = 0

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

    private fun isResultReady(): Boolean
    {
        val hasResult = glGetQueryObjectui(endQueryId, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE
        if (!hasResult)
           framesWithoutResult++
        return hasResult
    }

    private fun isStale() = (framesWithoutResult > 3)

    private fun getResult(): GpuTimeQueryResult
    {
        val startTime = glGetQueryObjectui64(startQueryId, GL_QUERY_RESULT)
        val endTime = glGetQueryObjectui64(endQueryId, GL_QUERY_RESULT)
        val result = resultPool.removeLastOrNull() ?: GpuTimeQueryResult()
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
        framesWithoutResult = 0
        timerPool.add(this)
    }

    private fun destroy()
    {
        glDeleteQueries(startQueryId)
        glDeleteQueries(endQueryId)
    }

    companion object
    {
        private val queryIdPool  = TIntArrayList(1000)
        private val timerPool    = ArrayList<GpuTimeQuery>()
        private val resultPool   = ArrayList<GpuTimeQueryResult>()
        private val timerStack   = ArrayDeque<GpuTimeQuery>()
        private val activeTimers = ArrayDeque<GpuTimeQuery>()
        private val writeResults = ArrayList<GpuTimeQueryResult>()
        private val readyResults = ArrayList<GpuTimeQueryResult>()

        /**
         * Returns all ready results. Safe to call from game thread.
         */
        fun getAllResults(): List<GpuTimeQueryResult> = readyResults

        /**
         * Polls the results of all ready timers and moves them to the ready results list.
         * Should only be called from the main/graphics thread at the beginning of the frame.
         */
        fun pollResults()
        {
            while (activeTimers.size > 0)
            {
                val timer = activeTimers.first()
                if (timer.isResultReady())
                {
                    if (timer.depth == 0) // This is the 'Frame' timer, flush results
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
                else if (timer.isStale())
                {
                    timer.destroy()
                    activeTimers.removeFirst()
                    continue // to next active timer
                }
                else return // Stop polling, check timer again next frame
            }
        }

        /**
         * Starts a new timer with the given [label].
         */
        fun start(label: CharSequence)
        {
            val timer = timerPool.removeLastOrNull() ?: GpuTimeQuery()
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

data class GpuTimeQueryResult(
    var label: StringBuilder = StringBuilder(50),
    var depth: Int = -1,
    var timeNanoSec: Long = -1L
)