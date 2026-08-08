package no.njoh.pulseengine.core.graphics.gpu.buffer

import no.njoh.pulseengine.core.shared.primitives.Mat4f
import org.joml.Matrix4f
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

    fun bindSubmittedRange() = buffer.bindSubmittedRange()

    fun markSubmittedDataInUse() = buffer.markSubmittedSegmentInUse()

    fun getSubmittedDataByteOffset() = buffer.submittedDataByteOffset

    fun clear() { size = 0 }
    
    fun destroy() = buffer.destroy()

    inline fun fill(amount: Int, fillBuffer: StreamingFloatBufferObject.(i: Int) -> Unit)
    {
        ensureWriteCapacity(size + amount)
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

    fun put(matrix: Mat4f)
    {
        val i = size
        size += 16
        System.arraycopy(matrix.data, matrix.offset, data, i, 16)
    }

    fun put(matrix: Matrix4f)
    {
        val i = size
        size += 16
        data[i +  0] = matrix.m00()
        data[i +  1] = matrix.m01()
        data[i +  2] = matrix.m02()
        data[i +  3] = matrix.m03()
        data[i +  4] = matrix.m10()
        data[i +  5] = matrix.m11()
        data[i +  6] = matrix.m12()
        data[i +  7] = matrix.m13()
        data[i +  8] = matrix.m20()
        data[i +  9] = matrix.m21()
        data[i + 10] = matrix.m22()
        data[i + 11] = matrix.m23()
        data[i + 12] = matrix.m30()
        data[i + 13] = matrix.m31()
        data[i + 14] = matrix.m32()
        data[i + 15] = matrix.m33()
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