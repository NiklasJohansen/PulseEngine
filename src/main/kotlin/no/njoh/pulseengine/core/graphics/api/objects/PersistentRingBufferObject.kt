package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.shared.utils.Extensions.formatted
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.ARBBufferStorage.GL_DYNAMIC_STORAGE_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.glBufferStorage
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL30.glBindBufferRange
import org.lwjgl.opengl.GL30.glMapBufferRange
import org.lwjgl.opengl.GL32.*
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.max

class PersistentRingBufferObject(
    private val target: Int,
    private val blockBinding: Int?,
    private val elementSizeBytes: Int,
    private val segmentCount: Int,
    initCapacity: Int
) {
    var id = 0;                       private set
    var submittedDataByteOffset = 0L; private set

    private var mappedFloatBuffer: FloatBuffer
    private var mappedIntBuffer: IntBuffer

    private val offsetAlignment       = getOffsetAlignment(target, blockBinding)
    private val segmentSyncObjects    = LongArray(segmentCount)
    private var segmentCapacity       = max(initCapacity, 16)
    private var segmentStrideBytes    = getSegmentStrideBytes(segmentCapacity)
    private var submittedElementCount = 0
    private var submittedSegmentIndex = -1
    private var writeSegmentIndex     = 0

    init
    {
        require(GlCapabilities.persistentMappedBuffers) { "Persistent mapped model buffers require OpenGL 4.4 or GL_ARB_buffer_storage" }
        require(segmentCount >= 2) { "At least two ring buffer segments are required" }

        val mappedByteBuffer = createMappedBuffer(segmentCount * segmentStrideBytes.toLong())
        mappedFloatBuffer = mappedByteBuffer.asFloatBuffer()
        mappedIntBuffer = mappedByteBuffer.asIntBuffer()
    }

    fun submit(data: FloatArray, size: Int)
    {
        ensureCapacity(size, data.size)
        waitForSegment(writeSegmentIndex)

        if (size > 0)
        {
            val offset = getSegmentElementOffset(writeSegmentIndex)
            mappedFloatBuffer.position(offset)
            mappedFloatBuffer.put(data, 0, size)
        }

        submitSegment(size)
    }

    fun submit(data: IntArray, size: Int)
    {
        ensureCapacity(size, data.size)
        waitForSegment(writeSegmentIndex)

        if (size > 0)
        {
            mappedIntBuffer.position(getSegmentElementOffset(writeSegmentIndex))
            mappedIntBuffer.put(data, 0, size)
        }

        submitSegment(size)
    }

    fun markSubmittedSegmentInUse()
    {
        val segmentIndex = submittedSegmentIndex
        if (segmentIndex < 0)
            return

        if (submittedElementCount > 0)
        {
            val syncObj = segmentSyncObjects[segmentIndex]
            if (syncObj != 0L) glDeleteSync(syncObj)

            segmentSyncObjects[segmentIndex] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        }

        writeSegmentIndex = (segmentIndex + 1) % segmentCount
        submittedSegmentIndex = -1
        submittedElementCount = 0
    }

    fun destroy()
    {
        if (id == 0) return

        repeat(segmentCount) { idx -> waitForSegment(idx) }

        glBindBuffer(target, id)
        glUnmapBuffer(target)
        glBindBuffer(target, 0)
        glDeleteBuffers(id)
        id = 0
    }
    
    private fun submitSegment(elementCount: Int)
    {
        submittedSegmentIndex   = writeSegmentIndex
        submittedElementCount   = elementCount
        submittedDataByteOffset = writeSegmentIndex * segmentStrideBytes.toLong()

        if (blockBinding != null)
        {
            val rangeSize = max(elementCount * elementSizeBytes, elementSizeBytes).toLong()
            glBindBufferRange(target, blockBinding, id, submittedDataByteOffset, rangeSize)
        }
    }

    private fun ensureCapacity(requiredCapacity: Int, cpuCapacity: Int)
    {
        if (requiredCapacity <= segmentCapacity)
            return

        destroy()

        val currentSizeKb  = (segmentStrideBytes * segmentCount) / 1024f
        segmentCapacity    = grow(segmentCapacity, max(requiredCapacity, cpuCapacity))
        segmentStrideBytes = getSegmentStrideBytes(segmentCapacity)

        val newSizeKb = (segmentStrideBytes * segmentCount) / 1024f
        Logger.debug { "Resizing persistent GPU buffer #$id (${currentSizeKb.formatted()} kB -> ${newSizeKb.formatted()} kB)" }

        val mappedByteBuffer = createMappedBuffer(segmentCount * segmentStrideBytes.toLong())
        mappedFloatBuffer = mappedByteBuffer.asFloatBuffer()
        mappedIntBuffer = mappedByteBuffer.asIntBuffer()
    }
    
    private fun createMappedBuffer(sizeInBytes: Long): ByteBuffer
    {
        val flags = GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT

        id = glGenBuffers()
        glBindBuffer(target, id)
        glBufferStorage(target, sizeInBytes, flags or GL_DYNAMIC_STORAGE_BIT)
        
        val mappedBuffer = glMapBufferRange(target, 0, sizeInBytes, flags) 
            ?: throw RuntimeException("Failed to map persistent ring buffer")
        
        glBindBuffer(target, 0)
        return mappedBuffer
    }

    private fun waitForSegment(segmentIndex: Int)
    {
        val syncObj = segmentSyncObjects[segmentIndex]
        if (syncObj == 0L) return

        var result = glClientWaitSync(syncObj, GL_SYNC_FLUSH_COMMANDS_BIT, 0L)
        while (result == GL_TIMEOUT_EXPIRED)
            result = glClientWaitSync(syncObj, GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000L) // timeout = 1 ms

        if (result == GL_WAIT_FAILED)
            Logger.warn { "Failed waiting for persistent model buffer sync object" }

        glDeleteSync(syncObj)
        segmentSyncObjects[segmentIndex] = 0L
    }

    private fun getOffsetAlignment(target: Int, blockBinding: Int?) =
        if (target == GL_SHADER_STORAGE_BUFFER && blockBinding != null)
            max(Int.SIZE_BYTES, glGetInteger(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT))
        else 
            Int.SIZE_BYTES
    
    private fun getSegmentElementOffset(segment: Int) = (segment * segmentStrideBytes) / elementSizeBytes
    
    private fun getSegmentStrideBytes(capacity: Int) = alignUp(capacity * elementSizeBytes, offsetAlignment)
    
    private fun alignUp(value: Int, alignment: Int): Int = ((value + alignment - 1) / alignment) * alignment

    private fun grow(current: Int, required: Int, minCapacity: Int = 16) = max(required, max(current * 2, minCapacity))
}