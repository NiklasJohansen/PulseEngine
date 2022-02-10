package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.ARBUniformBufferObject.GL_UNIFORM_BUFFER
import org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import org.lwjgl.system.MemoryUtil.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.min

sealed class BufferObject(
    val id: Int,
    private val target: Int,
    private val usage: Int,
    private val blockBinding: Int?,
    private var mappedBuffer: ByteBuffer
) {
    var count: Int = 0
    private var countToDraw = 0
    private var sizeInBytes = mappedBuffer.capacity().toLong()

    fun bind(): BufferObject
    {
        glBindBuffer(target, id)
        if (blockBinding != null)
            glBindBufferBase(target, blockBinding, id)
        return this
    }

    fun release(): BufferObject
    {
        glBindBuffer(target, 0)
        return this
    }

    fun submit()
    {
        if (count == 0)
            return

        val buffer = glMapBuffer(target, GL_WRITE_ONLY, mappedBuffer.capacity().toLong(), mappedBuffer)!!
        if (buffer !== mappedBuffer)
        {
            mappedBuffer = buffer
            onNewMappingBuffer(buffer)
        }

        onFillBuffer()
        glUnmapBuffer(target)
        countToDraw = count
        count = 0
    }

    fun draw(glDrawType: Int, stride: Int)
    {
        if (count > 0)
            submit()

        if (countToDraw == 0)
            return

        when (target)
        {
            GL_ARRAY_BUFFER -> glDrawArrays(glDrawType, 0, countToDraw / stride)
            GL_ELEMENT_ARRAY_BUFFER -> glDrawElements(glDrawType, countToDraw, GL_UNSIGNED_INT, 0)
        }
        countToDraw = 0
    }

    fun delete()
    {
        glDeleteBuffers(id)
    }

    fun ensureCapacity(newElements: Int)
    {
        if (!hasCapacityFor(newElements))
            changeSize((sizeInBytes * 1.5f).toLong())
    }

    private fun changeSize(newSizeInBytes: Long)
    {
        Logger.debug("Changing buffer object capacity from $sizeInBytes to: $newSizeInBytes bytes (${"${newSizeInBytes/1_000_000f}".format("%.2f")} MB)")
        sizeInBytes = newSizeInBytes
        glBindBuffer(target, id)
        glBufferData(target, sizeInBytes, usage)
        mappedBuffer = glMapBuffer(target, GL_WRITE_ONLY, sizeInBytes, mappedBuffer)!!
        onNewMappingBuffer(mappedBuffer)
        glUnmapBuffer(target)
    }

    protected abstract fun onFillBuffer()
    protected abstract fun onNewMappingBuffer(mappedBuffer: ByteBuffer)
    protected abstract fun hasCapacityFor (newElements: Int = 0): Boolean
    protected abstract fun onDelete()

    companion object
    {
        inline fun <reified T: BufferObject> createArrayBuffer(sizeInBytes: Long, usage: Int = GL_DYNAMIC_DRAW): T =
            createBuffer(sizeInBytes, usage, GL_ARRAY_BUFFER, null)

        inline fun <reified T: BufferObject> createShaderStorageBuffer(sizeInBytes: Long, blockBinding: Int, usage: Int = GL_DYNAMIC_DRAW): T =
            createBuffer(sizeInBytes, usage, GL_SHADER_STORAGE_BUFFER, blockBinding)

        inline fun <reified T: BufferObject> createUniformBuffer(sizeInBytes: Long, blockBinding: Int, usage: Int = GL_DYNAMIC_DRAW): T =
             createBuffer(sizeInBytes, usage, GL_UNIFORM_BUFFER, blockBinding)

        fun createElementBuffer(sizeInBytes: Long, usage: Int = GL_DYNAMIC_DRAW): IntBufferObject =
            createBuffer(sizeInBytes, usage, GL_ELEMENT_ARRAY_BUFFER, null)

        inline fun <reified T> createBuffer(sizeInBytes: Long, usage: Int, target: Int, blockBinding: Int?): T
        {
            val id = glGenBuffers()
            glBindBuffer(target, id)
            glBufferData(target, sizeInBytes, usage)
            val mappedBuffer = glMapBuffer(target, GL_WRITE_ONLY, sizeInBytes, null)!!
            glUnmapBuffer(target)
            glBindBuffer(target, 0)

            return when (T::class)
            {
                FloatBufferObject::class -> FloatBufferObject(id, target, usage, blockBinding, mappedBuffer) as T
                IntBufferObject::class -> IntBufferObject(id, target, usage, blockBinding, mappedBuffer) as T
                else -> throw IllegalArgumentException("type: ${T::class.simpleName} not supported")
            }
        }
    }
}

class FloatBufferObject(
    id: Int,
    target: Int,
    usage: Int,
    blockBinding: Int?,
    mappedBuffer: ByteBuffer
) : BufferObject(id, target, usage, blockBinding, mappedBuffer)
{
    private var mappedFloatBuffer: FloatBuffer = mappedBuffer.asFloatBuffer()
    private var bufferSize = mappedFloatBuffer.capacity()

    @PublishedApi
    internal var backingBuffer = memAllocFloat(bufferSize)

    override fun onNewMappingBuffer(mappedBuffer: ByteBuffer)
    {
        mappedFloatBuffer = mappedBuffer.asFloatBuffer()
        bufferSize = mappedFloatBuffer.capacity()
        backingBuffer.limit(min(backingBuffer.position(), bufferSize))
        backingBuffer.rewind()
        val newBuffer = memAllocFloat(bufferSize)
        newBuffer.put(backingBuffer)
        memFree(backingBuffer)
        backingBuffer = newBuffer
    }

    override fun onDelete() = memFree(backingBuffer)

    override fun onFillBuffer()
    {
        backingBuffer.flip()
        mappedFloatBuffer.clear()
        mappedFloatBuffer.put(backingBuffer)
        mappedFloatBuffer.flip()
        backingBuffer.clear()
    }

    override fun hasCapacityFor(newElements: Int): Boolean =
        count + newElements <= bufferSize

    inline fun fill(amount: Int, fillBuffer: FloatBuffer.() -> Unit)
    {
        ensureCapacity(amount)
        fillBuffer(backingBuffer)
        count += amount
    }
}

class IntBufferObject(
    id: Int,
    target: Int,
    usage: Int,
    blockBinding: Int?,
    mappedBuffer: ByteBuffer
) : BufferObject(id, target, usage, blockBinding, mappedBuffer)
{
    private var mappedIntBuffer: IntBuffer = mappedBuffer.asIntBuffer()
    private var bufferSize = mappedIntBuffer.capacity()

    @PublishedApi
    internal var backingBuffer = memAllocInt(bufferSize)

    override fun onNewMappingBuffer(mappedBuffer: ByteBuffer)
    {
        mappedIntBuffer = mappedBuffer.asIntBuffer()
        bufferSize = mappedIntBuffer.capacity()
        backingBuffer.limit(min(backingBuffer.position(), bufferSize))
        backingBuffer.rewind()
        val newBuffer = memAllocInt(bufferSize)
        newBuffer.put(backingBuffer)
        memFree(backingBuffer)
        backingBuffer = newBuffer
    }

    override fun onDelete() = memFree(backingBuffer)

    override fun onFillBuffer()
    {
        backingBuffer.flip()
        mappedIntBuffer.clear()
        mappedIntBuffer.put(backingBuffer)
        mappedIntBuffer.flip()
        backingBuffer.clear()
    }

    override fun hasCapacityFor(newElements: Int): Boolean =
        count + newElements <= bufferSize

    inline fun fill(amount: Int, fillBuffer: IntBuffer.() -> Unit)
    {
        ensureCapacity(amount)
        fillBuffer(backingBuffer)
        count += amount
    }
}