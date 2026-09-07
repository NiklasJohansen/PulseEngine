package no.njoh.pulseengine.core.graphics.gpu.buffer

import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import kotlin.math.max

class StreamingIntBufferObject private constructor(
    private val target: Int,
    initCapacity: Int,
    segmentCount: Int
) : ShaderStorageBufferObject {
    private val buffer = PersistentRingBufferObject(target, Int.SIZE_BYTES, segmentCount, initCapacity)

    val id: Int get() = buffer.id

    @PublishedApi internal var data = IntArray(initCapacity)
    @PublishedApi internal var size = 0

    fun bind() = glBindBuffer(target, buffer.id)

    fun release() = glBindBuffer(target, 0)
    
    fun submit() = buffer.submit(data, size)

    fun reserve(elementCount: Int) = buffer.reserve(elementCount)

    override fun bindStorageBuffer(binding: Int) = buffer.bindSubmittedRange(binding)

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

    fun put(v0: Int, v1: Int)
    {
        val i = size
        size += 2
        data[i    ] = v0
        data[i + 1] = v1
    }

    fun put(v0: Int, v1: Int, v2: Int)
    {
        val i = size
        size += 3
        data[i    ] = v0
        data[i + 1] = v1
        data[i + 2] = v2
    }

    fun put(v0: Int, v1: Int, v2: Int, v3: Int)
    {
        val i = size
        size += 4
        data[i    ] = v0
        data[i + 1] = v1
        data[i + 2] = v2
        data[i + 3] = v3
    }

    fun putCommand(
        indexCount: Int,    // Number of indices to draw for this mesh
        instanceCount: Int, // Number of instances to draw
        firstIndex: Int,    // Index offset inside the index buffer
        vertexOffset: Int,  // Value added to each index before indexing the vertex buffer
        baseInstance: Int   // ID of the first instance for instance data offsets
    ) {
        val i = size
        size += 5
        data[i    ] = indexCount
        data[i + 1] = instanceCount
        data[i + 2] = firstIndex
        data[i + 3] = vertexOffset
        data[i + 4] = baseInstance
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
            StreamingIntBufferObject(GL_ARRAY_BUFFER, initCapacity, segmentCount)

        fun createShaderStorageBuffer(initCapacity: Int = 0, segmentCount: Int = 3) =
            StreamingIntBufferObject(GL_SHADER_STORAGE_BUFFER, initCapacity, segmentCount)

        fun createDrawIndirectBuffer(initCapacity: Int = 0, segmentCount: Int = 3) =
            StreamingIntBufferObject(GL_DRAW_INDIRECT_BUFFER, initCapacity, segmentCount)
    }
}