package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.shared.utils.Extensions.formatted
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.ARBUniformBufferObject.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.*
import kotlin.math.max
import kotlin.math.min

class DoubleBufferedIntObject private constructor(
    val id: Int,
    private val target: Int,
    private val usage: Int,
    private val blockBinding: Int?,
    private var mappedByteBuffer: ByteBuffer
) {
    private var mappedSizeInBytes = mappedByteBuffer.capacity().toLong()
    private var mappedIntBuffer = mappedByteBuffer.asIntBuffer()

    @PublishedApi
    internal var writeBuffer: IntBuffer = MemoryUtil.memAllocInt(mappedIntBuffer.capacity())

    @PublishedApi
    internal var readBuffer: IntBuffer = MemoryUtil.memAllocInt(mappedIntBuffer.capacity())

    fun bind()
    {
        glBindBuffer(target, id)
        if (blockBinding != null)
            glBindBufferBase(target, blockBinding, id)
    }

    fun submit()
    {
        if (readBuffer.position() == 0)
            return // Nothing to submit

        if (readBuffer.capacity() > mappedIntBuffer.capacity())
        {
            val newSizeInBytes = 4L * readBuffer.capacity()
            Logger.debug("Resizing GPU buffer #$id (${(mappedSizeInBytes / 1024f).formatted()} kB -> ${(newSizeInBytes / 1024f).formatted()} kB)")
            glBindBuffer(target, id)
            glBufferData(target, newSizeInBytes, usage)
            mappedSizeInBytes = newSizeInBytes
        }

        val buffer = glMapBuffer(target, GL_WRITE_ONLY, mappedSizeInBytes, mappedByteBuffer)!!
        if (buffer !== mappedByteBuffer)
        {
            mappedByteBuffer = buffer
            mappedIntBuffer = buffer.asIntBuffer()
            mappedSizeInBytes = buffer.capacity().toLong()
        }

        readBuffer.flip()
        mappedIntBuffer.clear()
        mappedIntBuffer.put(readBuffer)
        mappedIntBuffer.flip()
        readBuffer.clear()

        glUnmapBuffer(target)
    }

    fun release()
    {
        glBindBuffer(target, 0)
    }

    fun delete()
    {
        glDeleteBuffers(id)
        MemoryUtil.memFree(readBuffer)
        MemoryUtil.memFree(writeBuffer)
    }

    fun swapBuffers()
    {
        // Resize read buffer to same size if write buffer has grown
        if (writeBuffer.capacity() > readBuffer.capacity())
            readBuffer = resizeBuffer(readBuffer, newCapacity = writeBuffer.capacity())

        // Swap buffer references
        readBuffer = writeBuffer.also { writeBuffer = readBuffer }
    }

    inline fun fill(amount: Int, fillBuffer: IntBuffer.() -> Unit)
    {
        if (amount > writeBuffer.remaining())
            writeBuffer = resizeBuffer(writeBuffer)

        fillBuffer(writeBuffer)
    }

    @PublishedApi
    internal fun resizeBuffer(
        currentBuffer: IntBuffer,
        newCapacity: Int = currentBuffer.capacity() * 3 / 2
    ): IntBuffer {
        var newBufferCapacity = newCapacity
        if (currentBuffer.capacity() == START_CAPACITY)
            newBufferCapacity = max(MIN_RESIZE_CAPACITY, newBufferCapacity)
        currentBuffer.limit(min(currentBuffer.position(), newBufferCapacity))
        currentBuffer.rewind()
        val newBuffer = MemoryUtil.memAllocInt(newBufferCapacity)
        newBuffer.put(currentBuffer)
        MemoryUtil.memFree(currentBuffer)
        return newBuffer
    }

    companion object
    {
        var START_CAPACITY = 1
        var MIN_RESIZE_CAPACITY = 512 // 2Kb

        fun createArrayBuffer(capacity: Int = START_CAPACITY, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(capacity, usage, GL_ARRAY_BUFFER, null)

        fun createShaderStorageBuffer(blockBinding: Int, capacity: Int = START_CAPACITY, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(capacity, usage, GL_SHADER_STORAGE_BUFFER, blockBinding)

        fun createUniformBuffer(blockBinding: Int, capacity: Int = START_CAPACITY, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(capacity, usage, GL_UNIFORM_BUFFER, blockBinding)

        fun createElementBuffer(sizeInBytes: Int = START_CAPACITY, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(sizeInBytes, usage, GL_ELEMENT_ARRAY_BUFFER, null)

        private fun createBuffer(capacity: Int, usage: Int, target: Int, blockBinding: Int?): DoubleBufferedIntObject
        {
            val id = glGenBuffers()
            val sizeInBytes = capacity * 4
            glBindBuffer(target, id)
            glBufferData(target, sizeInBytes.toLong(), usage)
            val mappedBuffer = glMapBuffer(target, GL_WRITE_ONLY, sizeInBytes.toLong(), null)!!
            glUnmapBuffer(target)
            glBindBuffer(target, 0)
            return DoubleBufferedIntObject(id, target, usage, blockBinding, mappedBuffer)
        }
    }
}