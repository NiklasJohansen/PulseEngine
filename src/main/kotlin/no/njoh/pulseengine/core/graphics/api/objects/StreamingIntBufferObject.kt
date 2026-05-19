package no.njoh.pulseengine.core.graphics.api.objects

import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.glBindBuffer
import kotlin.math.max

class StreamingIntBufferObject private constructor(
    private val target: Int,
    blockBinding: Int?,
    initCapacity: Int,
    segmentCount: Int
) {
    private val buffer = PersistentRingBufferObject(target, blockBinding, Int.SIZE_BYTES, segmentCount, initCapacity)

    @PublishedApi internal var data = IntArray(initCapacity)
    @PublishedApi internal var size = 0

    fun bind() = glBindBuffer(target, buffer.id)

    fun release() = glBindBuffer(target, 0)
    
    fun submit() = buffer.submit(data, size)

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
    }
}