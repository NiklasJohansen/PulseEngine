package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.shared.utils.Extensions.formatted
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.ARBUniformBufferObject.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import java.nio.ByteBuffer
import kotlin.math.max

class DoubleBufferedFloatObject private constructor(
    val id: Int,
    private val target: Int,
    private val usage: Int,
    private val blockBinding: Int?,
    private var mappedByteBuffer: ByteBuffer
) {
    private var mappedSizeInBytes = mappedByteBuffer.capacity().toLong()
    private var mappedFloatBuffer = mappedByteBuffer.asFloatBuffer()

    @PublishedApi internal var writeArray = FloatArray(mappedFloatBuffer.capacity())
    @PublishedApi internal var readArray  = FloatArray(mappedFloatBuffer.capacity())
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

        if (readArray.size > mappedFloatBuffer.capacity() )
        {
            val newSizeInBytes = 4L * readArray.size
            Logger.debug("Resizing GPU buffer #$id (${(mappedSizeInBytes / 1024f).formatted()} kB -> ${(newSizeInBytes / 1024f).formatted()} kB)")
            glBindBuffer(target, id)
            glBufferData(target, newSizeInBytes, usage)
            mappedSizeInBytes = newSizeInBytes
        }

        val buffer = glMapBuffer(target, GL_WRITE_ONLY, mappedSizeInBytes, mappedByteBuffer)!!
        if (buffer !== mappedByteBuffer)
        {
            mappedByteBuffer  = buffer
            mappedFloatBuffer = buffer.asFloatBuffer()
            mappedSizeInBytes = buffer.capacity().toLong()
        }

        mappedFloatBuffer.clear()
        mappedFloatBuffer.put(readArray, 0, readSize)
        mappedFloatBuffer.flip()

        glUnmapBuffer(target)
    }

    fun release()
    {
        glBindBuffer(target, 0)
    }

    fun delete()
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

    inline fun fill(amount: Int, fillBuffer: DoubleBufferedFloatObject.(i: Int) -> Unit)
    {
        if (writeIndex + amount >= writeArray.size)
            writeArray = resizeArray(writeArray, newCapacity = (writeIndex + amount) * 3 / 2)

        fillBuffer(this, writeIndex)
    }

    fun put(v: Float)
    {
        writeArray[writeIndex++] = v
    }

    fun put(v0: Float, v1: Float)
    {
        val i = writeIndex
        writeIndex += 2
        writeArray[i    ] = v0
        writeArray[i + 1] = v1
    }

    fun put(v0: Float, v1: Float, v2: Float)
    {
        val i = writeIndex
        writeIndex += 3
        writeArray[i    ] = v0
        writeArray[i + 1] = v1
        writeArray[i + 2] = v2
    }

    fun put(v0: Float, v1: Float, v2: Float, v3: Float)
    {
        val i = writeIndex
        writeIndex += 4
        writeArray[i    ] = v0
        writeArray[i + 1] = v1
        writeArray[i + 2] = v2
        writeArray[i + 3] = v3
    }

    @PublishedApi
    internal fun resizeArray(currentArray: FloatArray, newCapacity: Int) =
        currentArray.copyInto(FloatArray(max(newCapacity, MIN_RESIZE_CAPACITY)))

    companion object
    {
        var MIN_RESIZE_CAPACITY = 1024 // 4kB

        fun createArrayBuffer(initCapacity: Int = 0, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(initCapacity, usage, GL_ARRAY_BUFFER, null)

        fun createShaderStorageBuffer(blockBinding: Int, initCapacity: Int = 0, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(initCapacity, usage, GL_SHADER_STORAGE_BUFFER, blockBinding)

        fun createUniformBuffer(blockBinding: Int, initCapacity: Int = 0, usage: Int = GL_DYNAMIC_DRAW) =
            createBuffer(initCapacity, usage, GL_UNIFORM_BUFFER, blockBinding)

        private fun createBuffer(initCapacity: Int, usage: Int, target: Int, blockBinding: Int?): DoubleBufferedFloatObject
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
            return DoubleBufferedFloatObject(id, target, usage, blockBinding, mappedBuffer ?: ByteBuffer.allocate(0))
        }
    }
}