package no.njoh.pulseengine.core.graphics.api.objects

import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import kotlin.math.max

class StreamingFloatBufferObject private constructor(
    val target: Int,
    blockBinding: Int?,
    initCapacity: Int,
    segmentCount: Int
) {
    private val buffer = PersistentRingBufferObject(target, blockBinding, Float.SIZE_BYTES, segmentCount, initCapacity)

    @PublishedApi internal var data = FloatArray(initCapacity)
    @PublishedApi internal var size = 0

    fun bind() = glBindBuffer(target, buffer.id)

    fun release() = glBindBuffer(target, 0)

    fun submit() = buffer.submit(data, size)

    fun markSubmittedDataInUse() = buffer.markSubmittedSegmentInUse()

    fun getSubmittedDataByteOffset() = buffer.submittedDataByteOffset

    fun clear() { size = 0 }
    
    fun destroy() = buffer.destroy()

    inline fun fill(amount: Int, fillBuffer: StreamingFloatBufferObject.(i: Int) -> Unit)
    {
        ensureCapacity(size + amount)
        fillBuffer(this, size)
    }

    fun put(v: Float)
    {
        data[size++] = v
    }

    fun put(v0: Float, v1: Float, v2: Float, v3: Float)
    {
        val i = size
        size += 4
        data[i    ] = v0
        data[i + 1] = v1
        data[i + 2] = v2
        data[i + 3] = v3
    }

    @PublishedApi
    internal fun ensureCapacity(requiredCapacity: Int)
    {
        if (requiredCapacity > data.size)
        {
            val newCapacity = max(requiredCapacity, max(data.size * 2, 16))
            data = data.copyInto(FloatArray(newCapacity))
        }
    }

    companion object
    {
        fun createShaderStorageBuffer(blockBinding: Int, initCapacity: Int = 0, segmentCount: Int = 3) =
            StreamingFloatBufferObject(GL_SHADER_STORAGE_BUFFER, blockBinding, initCapacity, segmentCount)
    }
}