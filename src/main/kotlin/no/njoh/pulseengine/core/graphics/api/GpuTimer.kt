package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL33.GL_TIME_ELAPSED

/**
 * Utility class for measuring GPU time.
 */
class GpuTimer
{
    private var initialized       = false
    private var queryIds          = IntArray(5)
    private var lastQueryTimeMs   = 0f
    private var readIndex         = 0
    private var writeIndex        = 0

    fun start()
    {
        if (!initialized)
        {
            glGenQueries(queryIds)
            initialized = true
        }
        glBeginQuery(GL_TIME_ELAPSED, queryIds[writeIndex])
        writeIndex = (writeIndex + 1) % queryIds.size
    }

    fun stop(): Float
    {
        glEndQuery(GL_TIME_ELAPSED)

        if (readIndex != writeIndex)
        {
            val id = queryIds[readIndex]
            if (glGetQueryObjecti(id, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE)
            {
                lastQueryTimeMs = glGetQueryObjectui(id, GL_QUERY_RESULT) / 1_000_000f
                readIndex = (readIndex + 1) % queryIds.size
            }
        }

        return lastQueryTimeMs
    }
}