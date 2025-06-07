package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.shared.utils.Extensions.formatted
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.ARBUniformBufferObject.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import java.nio.ByteBuffer
import kotlin.math.max

class DoubleBufferedIntObject private constructor(
    val id: Int,
    private val target: Int,
    private val usage: Int,
    private val blockBinding: Int?,
    private var mappedByteBuffer: ByteBuffer
) {
    private var mappedSizeInBytes = mappedByteBuffer.capacity().toLong()
    private var mappedIntBuffer = mappedByteBuffer.asIntBuffer()

    @PublishedApi internal var writeArray = IntArray(mappedIntBuffer.capacity())
    @PublishedApi internal var readArray  = IntArray(mappedIntBuffer.capacity())
    @PublishedApi internal var writeIndex = 0
    @PublishedApi internal var readSize   = 0

    fun bind()
    {
        glBindBuffer(target, id)
        if (blockBinding != null)
            glBindBufferBase(target, blockBinding, id)
    }

    fun submit()
    {
        if (readSize == 0)
            return // Nothing to submit

        if (readArray.size > mappedIntBuffer.capacity() )
        {
            val newSizeInBytes = 4L * readArray.size
            Logger.debug { "Resizing GPU buffer #$id (${(mappedSizeInBytes / 1024f).formatted()} kB -> ${(newSizeInBytes / 1024f).formatted()} kB)" }
            glBindBuffer(target, id)
            glBufferData(target, newSizeInBytes, usage)
            mappedSizeInBytes = newSizeInBytes
        }

        val buffer = glMapBuffer(target, GL_WRITE_ONLY, mappedSizeInBytes, mappedByteBuffer)!!
        if (buffer !== mappedByteBuffer)
        {
            mappedByteBuffer  = buffer
            mappedIntBuffer = buffer.asIntBuffer()
            mappedSizeInBytes = buffer.capacity().toLong()
        }

        mappedIntBuffer.clear()
        mappedIntBuffer.put(readArray, 0, readSize)
        mappedIntBuffer.flip()

        glUnmapBuffer(target)
    }

    fun release()
    {
        glBindBuffer(target, 0)
    }

    fun destroy()
    {
        glDeleteBuffers(id)
    }

    fun swapBuffers()
    {
        // Resize read buffer to same size if write buffer has grown
        if (writeArray.size > readArray.size)
            readArray = resizeArray(readArray, newCapacity = writeArray.size)

        // Swap buffer references
        readArray = writeArray.also { writeArray = readArray }
        readSize = writeIndex
        writeIndex = 0
    }

    inline fun fill(amount: Int, fillBuffer: DoubleBufferedIntObject.(i: Int) -> Unit)
    {
        if (writeIndex + amount >= writeArray.size)
            writeArray = resizeArray(writeArray, newCapacity = (writeIndex + amount) * 3 / 2)

        fillBuffer(this, writeIndex)
    }

    fun put(v: Int)
    {
        writeArray[writeIndex++] = v
    }

    fun put(v0: Int, v1: Int)
    {
        val i = writeIndex
        writeIndex += 2
        writeArray[i    ] = v0
        writeArray[i + 1] = v1
    }

    fun put(v0: Int, v1: Int, v2: Int)
    {
        val i = writeIndex
        writeIndex += 3
        writeArray[i    ] = v0
        writeArray[i + 1] = v1
        writeArray[i + 2] = v2
    }

    fun put(v0: Int, v1: Int, v2: Int, v3: Int)
    {
        val i = writeIndex
        writeIndex += 4
        writeArray[i    ] = v0
        writeArray[i + 1] = v1
        writeArray[i + 2] = v2
        writeArray[i + 3] = v3
    }

    @PublishedApi
    internal fun resizeArray(currentArray: IntArray, newCapacity: Int) =
        currentArray.copyInto(IntArray(max(newCapacity, MIN_RESIZE_CAPACITY)))

    companion object
    {
        var MIN_RESIZE_CAPACITY = 1024 // 4kB

        fun createArrayBuffer(initCapacity: Int = 0, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(initCapacity, usage, GL_ARRAY_BUFFER, null)

        fun createShaderStorageBuffer(blockBinding: Int, initCapacity: Int = 0, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(initCapacity, usage, GL_SHADER_STORAGE_BUFFER, blockBinding)

        fun createUniformBuffer(blockBinding: Int, initCapacity: Int = 0, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(initCapacity, usage, GL_UNIFORM_BUFFER, blockBinding)

        fun createElementBuffer(initCapacity: Int = 0, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(initCapacity, usage, GL_ELEMENT_ARRAY_BUFFER, null)

        private fun createBuffer(initCapacity: Int, usage: Int, target: Int, blockBinding: Int?): DoubleBufferedIntObject
        {
            val id = glGenBuffers()
            var mappedBuffer = null as ByteBuffer?
            val sizeInBytes = initCapacity * 4L
            if (sizeInBytes > 0)
            {
                glBindBuffer(target, id)
                glBufferData(target, sizeInBytes, usage)
                mappedBuffer = glMapBuffer(target, GL_WRITE_ONLY, sizeInBytes, null)!!
                glUnmapBuffer(target)
                glBindBuffer(target, 0)
            }
            return DoubleBufferedIntObject(id, target, usage, blockBinding, mappedBuffer ?: ByteBuffer.allocate(0))
        }
    }
}