package no.njoh.pulseengine.core.graphics.api.objects

import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import kotlin.math.max

class StreamingIntBufferObject private constructor(
    private val target: Int,
    blockBinding: Int?,
    initCapacity: Int,
    segmentCount: Int
) {
    private val buffer = PersistentRingBufferObject(target, blockBinding, Int.SIZE_BYTES, segmentCount, initCapacity)

    val id: Int get() = buffer.id

    @PublishedApi internal var data = IntArray(initCapacity)
    @PublishedApi internal var size = 0

    fun bind() = glBindBuffer(target, buffer.id)

    fun release() = glBindBuffer(target, 0)
    
    fun submit() = buffer.submit(data, size)

    fun reserve(elementCount: Int) = buffer.reserve(elementCount)

    fun bindSubmittedRange() = buffer.bindSubmittedRange()

    fun markSubmittedDataInUse() = buffer.markSubmittedSegmentInUse()

    fun getSubmittedDataByteOffset() = buffer.submittedDataByteOffset
    
    fun clear() { size = 0 }
    
    fun destroy() = buffer.destroy()

    inline fun fill(amount: Int, fillBuffer: StreamingIntBufferObject.(i: Int) -> Unit)
    {
        ensureWriteCapacity(size + amount)
        fillBuffer(this, size)
    }

    fun put(v: Int)
    {
        data[size++] = v
    }

    fun put(v0: Int, v1: Int, v2: Int, v3: Int, v4: Int)
    {
        val i = size
        size += 5
        data[i    ] = v0
        data[i + 1] = v1
        data[i + 2] = v2
        data[i + 3] = v3
        data[i + 4] = v4
    }

    operator fun set(index: Int, value: Int)
    {
        ensureWriteCapacity(index + 1)
        data[index] = value
        if (index >= size) 
            size = index + 1
    }

    @PublishedApi
    internal fun ensureWriteCapacity(requiredCapacity: Int)
    {
        if (requiredCapacity > data.size)
        {
            val newCapacity = max(requiredCapacity, max(data.size * 2, 16))
            data = data.copyInto(IntArray(newCapacity))
        }
    }

    companion object
    {
        fun createArrayBuffer(initCapacity: Int = 0, segmentCount: Int = 3) =
            StreamingIntBufferObject(GL_ARRAY_BUFFER, null, initCapacity, segmentCount)

        fun createShaderStorageBuffer(blockBinding: Int, initCapacity: Int = 0, segmentCount: Int = 3) =
            StreamingIntBufferObject(GL_SHADER_STORAGE_BUFFER, blockBinding, initCapacity, segmentCount)

        fun createDrawIndirectBuffer(initCapacity: Int = 0, segmentCount: Int = 3) =
            StreamingIntBufferObject(GL_DRAW_INDIRECT_BUFFER, null, initCapacity, segmentCount)
    }
}
