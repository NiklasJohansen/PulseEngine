package no.njoh.pulseengine.core.graphics.gpu.buffer

import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.incrementUploadedBytes
import no.njoh.pulseengine.core.shared.utils.Extensions.formatted
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.ARBBufferStorage.GL_DYNAMIC_STORAGE_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT
import org.lwjgl.opengl.ARBBufferStorage.glBufferStorage
import org.lwjgl.opengl.GL11.glGetError
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

    private val offsetAlignment       = getOffsetAlignment(target)
    private val segmentSyncObjects    = LongArray(segmentCount)
    private var segmentCapacity       = max(initCapacity, 16)
    private var segmentStrideBytes    = getSegmentStrideBytes(segmentCapacity)
    private var submittedElementCount = 0
    private var submittedSegmentIndex = -1
    private var writeSegmentIndex     = 0

    init
    {
        GlCapabilities.requireFullGraphics("Persistent GPU ring buffers")
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
            incrementUploadedBytes(size.toLong() * elementSizeBytes)
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
            incrementUploadedBytes(size.toLong() * elementSizeBytes)
        }

        submitSegment(size)
    }

    fun reserve(elementCount: Int)
    {
        ensureCapacity(elementCount, elementCount)
        waitForSegment(writeSegmentIndex)
        submitSegment(elementCount)
    }

    fun bindSubmittedRange()
    {
        val binding = blockBinding ?: return
        bindSubmittedRange(binding)
    }

    fun bindSubmittedRange(binding: Int)
    {
        val rangeSize = max(submittedElementCount * elementSizeBytes, elementSizeBytes).toLong()
        glBindBufferRange(target, binding, id, submittedDataByteOffset, rangeSize)
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
    }

    fun destroy()
    {
        if (id == 0) return

        for (idx in segmentSyncObjects.indices)
        {
            val syncObj = segmentSyncObjects[idx]
            if (syncObj != 0L) glDeleteSync(syncObj)
            segmentSyncObjects[idx] = 0L
        }

        glDeleteBuffers(id)
        id = 0
    }
    
    private fun submitSegment(elementCount: Int)
    {
        submittedSegmentIndex   = writeSegmentIndex
        submittedElementCount   = elementCount
        submittedDataByteOffset = writeSegmentIndex * segmentStrideBytes.toLong()

        if (blockBinding != null)
            bindSubmittedRange()
    }

    private fun ensureCapacity(requiredCapacity: Int, cpuCapacity: Int)
    {
        if (requiredCapacity <= segmentCapacity)
            return

        repeat(segmentCount) { idx -> waitForSegment(idx) }
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

        val waitStartNs = System.nanoTime()
        val result = glClientWaitSync(syncObj, GL_SYNC_FLUSH_COMMANDS_BIT, MAX_SEGMENT_WAIT_NS)
        if (result == GL_ALREADY_SIGNALED || result == GL_CONDITION_SATISFIED)
        {
            glDeleteSync(syncObj)
            segmentSyncObjects[segmentIndex] = 0L
            return
        }

        val reason = if (result == GL_TIMEOUT_EXPIRED) "Timed out" else "Failed"
        val waitTimeMs = (System.nanoTime() - waitStartNs) / 1_000_000L
        val glError = glGetError()
        throw IllegalStateException(
            "$reason waiting for persistent GPU buffer fence: buffer=$id, target=$target, binding=$blockBinding, " +
            "segment=$segmentIndex/$segmentCount, fence=$syncObj, wait=${waitTimeMs}ms, result=$result, " +
            "glError=0x${Integer.toHexString(glError)}, contextGeneration=${GlCapabilities.contextGeneration}"
        )
    }

    private fun getOffsetAlignment(target: Int) =
        if (target == GL_SHADER_STORAGE_BUFFER)
            max(Int.SIZE_BYTES, glGetInteger(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT))
        else 
            Int.SIZE_BYTES

    private fun getSegmentElementOffset(segment: Int) = (segment * segmentStrideBytes) / elementSizeBytes
    
    private fun getSegmentStrideBytes(capacity: Int) = alignUp(capacity * elementSizeBytes, offsetAlignment)
    
    private fun alignUp(value: Int, alignment: Int): Int = ((value + alignment - 1) / alignment) * alignment

    private fun grow(current: Int, required: Int, minCapacity: Int = 16) = max(required, max(current * 2, minCapacity))

    companion object
    {
        private const val MAX_SEGMENT_WAIT_NS = 5_000_000_000L
    }
}
