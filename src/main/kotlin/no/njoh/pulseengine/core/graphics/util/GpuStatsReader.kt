package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15.GL_STREAM_READ
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL15.glGetBufferSubData
import org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER
import org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER
import org.lwjgl.opengl.GL31.glCopyBufferSubData
import org.lwjgl.opengl.GL32.GL_ALREADY_SIGNALED
import org.lwjgl.opengl.GL32.GL_CONDITION_SATISFIED
import org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE
import org.lwjgl.opengl.GL32.GL_TIMEOUT_EXPIRED
import org.lwjgl.opengl.GL32.GL_WAIT_FAILED
import org.lwjgl.opengl.GL32.glClientWaitSync
import org.lwjgl.opengl.GL32.glDeleteSync
import org.lwjgl.opengl.GL32.glFenceSync

/**
 * Asynchronously reads GPU-written indirect draw commands and feeds visible draw work back into
 * [GpuProfiler]. Uses only GL 4.1-era copy buffers and sync objects, and is created lazily by the
 * GPU-culled draw path.
 */
class GpuStatsReader(initialCommandCapacity: Int = 256)
{
    private val activeQueries = DynamicList<Query>(INITIAL_QUERY_CAPACITY)
    private val queryPool = DynamicList<Query>(INITIAL_QUERY_CAPACITY)
    private var commandData = BufferUtils.createIntBuffer(initialCommandCapacity * INDIRECT_COMMAND_INTS)

    fun capture(commandBufferId: Int, byteOffset: Long, commandCount: Int)
    {
        if (commandCount <= 0) return

        val query = queryPool.removeLastOrNull() ?: Query()
        if (query.capture(commandBufferId, byteOffset, commandCount))
            activeQueries += query
        else
            queryPool += query
    }

    fun pollResults()
    {
        var index = 0
        while (index < activeQueries.size)
        {
            val query = activeQueries[index]
            if (isResultReady(query))
            {
                removeActiveAt(index)
                readResult(query)
                query.recycle()
                queryPool += query
            }
            else if (query.hasFailed || query.framesWithoutResult > MAX_FRAMES_WITHOUT_RESULT)
            {
                removeActiveAt(index)
                query.destroy()
                queryPool += query
            }
            else index++
        }
    }

    private fun isResultReady(query: Query): Boolean =
        when (glClientWaitSync(query.syncObject, 0, 0L))
        {
            GL_ALREADY_SIGNALED, GL_CONDITION_SATISFIED -> true
            GL_TIMEOUT_EXPIRED ->
            {
                query.framesWithoutResult++
                false
            }
            GL_WAIT_FAILED ->
            {
                query.hasFailed = true
                false
            }
            else -> false
        }

    private fun readResult(query: Query)
    {
        ensureCommandDataCapacity(query.commandCount * INDIRECT_COMMAND_INTS)

        commandData.clear()
        commandData.limit(query.commandCount * INDIRECT_COMMAND_INTS)

        glBindBuffer(GL_COPY_READ_BUFFER, query.bufferId)
        glGetBufferSubData(GL_COPY_READ_BUFFER, 0L, commandData)
        glBindBuffer(GL_COPY_READ_BUFFER, 0)

        var visibleCommands = 0L
        var visibleInstances = 0L
        var visibleTriangles = 0L

        for (command in 0 until query.commandCount)
        {
            val base = command * INDIRECT_COMMAND_INTS
            val indexCount = commandData.get(base + INDEX_COUNT_OFFSET).asUnsignedLong()
            val instanceCount = commandData.get(base + INSTANCE_COUNT_OFFSET).asUnsignedLong()

            if (instanceCount > 0L)
                visibleCommands++

            visibleInstances += instanceCount
            visibleTriangles += instanceCount * (indexCount / 3L)
        }

        GpuProfiler.incrementDrawStats(
            drawCommands = visibleCommands,
            triangles = visibleTriangles,
            instances = visibleInstances
        )
    }

    private fun removeActiveAt(index: Int)
    {
        val lastIndex = activeQueries.size - 1
        if (index != lastIndex)
            activeQueries[index] = activeQueries[lastIndex]
        activeQueries.removeLastOrNull()
    }

    private fun ensureCommandDataCapacity(requiredInts: Int)
    {
        if (commandData.capacity() < requiredInts)
            commandData = BufferUtils.createIntBuffer(requiredInts)
    }

    private fun Int.asUnsignedLong() = toLong() and 0xFFFF_FFFFL

    private class Query
    {
        var bufferId = 0
        var capacityBytes = 0
        var commandCount = 0
        var syncObject = 0L
        var framesWithoutResult = 0
        var hasFailed = false

        fun capture(commandBufferId: Int, byteOffset: Long, commandCount: Int): Boolean
        {
            val byteCount = commandCount * INDIRECT_COMMAND_STRIDE_BYTES
            ensureCapacity(byteCount)

            glBindBuffer(GL_COPY_READ_BUFFER, commandBufferId)
            glBindBuffer(GL_COPY_WRITE_BUFFER, bufferId)
            glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, byteOffset, 0L, byteCount.toLong())
            syncObject = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
            glBindBuffer(GL_COPY_READ_BUFFER, 0)
            glBindBuffer(GL_COPY_WRITE_BUFFER, 0)

            this.commandCount = commandCount
            framesWithoutResult = 0
            hasFailed = syncObject == 0L

            return !hasFailed
        }

        fun recycle()
        {
            if (syncObject != 0L) 
                glDeleteSync(syncObject)

            syncObject = 0L
            commandCount = 0
            framesWithoutResult = 0
            hasFailed = false
        }

        fun destroy()
        {
            recycle()

            if (bufferId != 0) 
                glDeleteBuffers(bufferId)

            bufferId = 0
            capacityBytes = 0
        }

        private fun ensureCapacity(requiredBytes: Int)
        {
            if (bufferId == 0)
                bufferId = glGenBuffers()

            if (capacityBytes >= requiredBytes)
                return

            glBindBuffer(GL_COPY_WRITE_BUFFER, bufferId)
            glBufferData(GL_COPY_WRITE_BUFFER, requiredBytes.toLong(), GL_STREAM_READ)
            glBindBuffer(GL_COPY_WRITE_BUFFER, 0)
            capacityBytes = requiredBytes
        }
    }

    companion object
    {
        private const val INITIAL_QUERY_CAPACITY = 32
        private const val MAX_FRAMES_WITHOUT_RESULT = 4
        private const val INDIRECT_COMMAND_INTS = 5
        private const val INDIRECT_COMMAND_STRIDE_BYTES = INDIRECT_COMMAND_INTS * Int.SIZE_BYTES
        private const val INDEX_COUNT_OFFSET = 0
        private const val INSTANCE_COUNT_OFFSET = 1
    }
}