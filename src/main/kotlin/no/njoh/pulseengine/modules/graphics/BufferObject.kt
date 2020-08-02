package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.util.Logger
import org.lwjgl.opengl.ARBUniformBufferObject.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.system.MemoryUtil
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

sealed class BufferObject(
    val id: Int,
    private val target: Int,
    private val usage: Int,
    private val blockBinding: Int?,
    private var maxSize: Long
) {
    protected var byteBuffer: ByteBuffer = MemoryUtil.memAlloc(maxSize.toInt())
    protected var size: Int = 0
    private var countToDraw = 0

    fun bind() = this.also {
        glBindBuffer(target, id)
        if (blockBinding != null)
            glBindBufferBase(target, blockBinding, id)
    }

    fun release() = this.also { glBindBuffer(target, 0) }

    fun growSize(factor: Float = 2f)
    {
        changeSize((maxSize * factor).toLong())
    }

    private fun changeSize(size: Long)
    {
        Logger.debug("Changing buffer object capacity from $maxSize to: $size bytes (${"${size/1_000_000f}".format("%.2f")} MB)")
        maxSize = size
        glBindBuffer(target, id)
        glBufferData(target, maxSize, usage)
        byteBuffer = glMapBuffer(target, GL_WRITE_ONLY, maxSize, byteBuffer)!!
        glUnmapBuffer(target)
        setTypeBuffer()
    }

    fun flush()
    {
        if (size == 0)
            return

        if (byteBuffer.position() != 0)
        {
            byteBuffer.flip()
            flipTypeBuffer()
        }

        byteBuffer = glMapBuffer(target, GL_WRITE_ONLY, byteBuffer.capacity().toLong(), byteBuffer)!!
        byteBuffer.clear()
        setTypeBuffer()
        glUnmapBuffer(target)
        countToDraw = size
        size = 0
    }

    fun draw(glDrawType: Int, stride: Int)
    {
        if (size > 0)
            flush()

        if (countToDraw == 0)
            return

        when (target)
        {
            GL_ARRAY_BUFFER -> glDrawArrays(glDrawType, 0, countToDraw / stride)
            GL_ELEMENT_ARRAY_BUFFER -> glDrawElements(glDrawType, countToDraw / stride, GL_UNSIGNED_INT, 0)
        }
        countToDraw = 0
    }

    fun delete()
    {
        MemoryUtil.memFree(byteBuffer)
        glDeleteBuffers(id)
    }

    abstract fun flipTypeBuffer()
    abstract fun setTypeBuffer()
    abstract fun hasCapacityfor (elements: Int = 0): Boolean

    companion object
    {
        inline fun <reified T: BufferObject> createAndBind(size: Long, usage: Int = GL_DYNAMIC_DRAW): T
             = createAndBindBuffer(size, usage, GL_ARRAY_BUFFER, null)

        inline fun <reified T: BufferObject> createAndBindUniformBuffer(size: Long, blockBinding: Int, usage: Int = GL_DYNAMIC_DRAW): T =
             createAndBindBuffer(size, usage, GL_UNIFORM_BUFFER, blockBinding)

        fun createAndBindElementBuffer(size: Long, usage: Int = GL_DYNAMIC_DRAW): IntBufferObject
            = createAndBindBuffer(size, usage, GL_ELEMENT_ARRAY_BUFFER, null)

        inline fun <reified T> createAndBindBuffer(size: Long, usage: Int, target: Int, blockBinding: Int?): T
        {
            val id = glGenBuffers()

            glBindBuffer(target, id)
            glBufferData(target, size, usage)

            return when (T::class)
            {
                FloatBufferObject::class -> FloatBufferObject(id, target, usage, blockBinding, size) as T
                IntBufferObject::class -> IntBufferObject(id, target, usage, blockBinding, size) as T
                ByteBufferObject::class -> ByteBufferObject(id, target, usage, blockBinding, size) as T
                else -> throw IllegalArgumentException("type: ${T::class.simpleName} not supported")
            }
        }
    }
}

class FloatBufferObject(id: Int, target: Int, usage: Int, blockBinding: Int?, maxSize: Long) : BufferObject(id, target, usage, blockBinding, maxSize)
{
    private var floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()

    override fun hasCapacityfor (elements: Int): Boolean = floatBuffer.position() + elements <= floatBuffer.capacity()

    override fun setTypeBuffer()
    {
        floatBuffer = byteBuffer.asFloatBuffer()
    }

    override fun flipTypeBuffer()
    {
        floatBuffer.flip()
    }

    fun put(value: Float): FloatBufferObject
    {
        floatBuffer.put(value)
        size += 1
        return this
    }

    fun put(v0: Float, v1: Float, v2: Float): FloatBufferObject
    {
        if (!hasCapacityfor (3))
            growSize()

        floatBuffer.put(v0)
        floatBuffer.put(v1)
        floatBuffer.put(v2)

        size += 3
        return this
    }

    fun put(v0: Float, v1: Float, v2: Float, v3: Float): FloatBufferObject
    {
        if (!hasCapacityfor (4))
            growSize()

        floatBuffer.put(v0)
        floatBuffer.put(v1)
        floatBuffer.put(v2)
        floatBuffer.put(v3)

        size += 4
        return this
    }

    fun put(v0: Float, v1: Float, v2: Float, v3: Float, v4: Float, v5: Float): FloatBufferObject
    {
        if (!hasCapacityfor (6))
            growSize()

        floatBuffer.put(v0)
        floatBuffer.put(v1)
        floatBuffer.put(v2)
        floatBuffer.put(v3)
        floatBuffer.put(v4)
        floatBuffer.put(v5)

        size += 10
        return this
    }

    fun put(v0: Float, v1: Float, v2: Float, v3: Float, v4: Float, v5: Float, v6: Float, v7: Float): FloatBufferObject
    {
        if (!hasCapacityfor (8))
            growSize()

        floatBuffer.put(v0)
        floatBuffer.put(v1)
        floatBuffer.put(v2)
        floatBuffer.put(v3)
        floatBuffer.put(v4)
        floatBuffer.put(v5)
        floatBuffer.put(v6)
        floatBuffer.put(v7)

        size += 8
        return this
    }

    fun put(v0: Float, v1: Float, v2: Float, v3: Float, v4: Float, v5: Float, v6: Float, v7: Float, v8: Float, v9: Float): FloatBufferObject
    {
        if (!hasCapacityfor (10))
            growSize()

        floatBuffer.put(v0)
        floatBuffer.put(v1)
        floatBuffer.put(v2)
        floatBuffer.put(v3)
        floatBuffer.put(v4)
        floatBuffer.put(v5)
        floatBuffer.put(v6)
        floatBuffer.put(v7)
        floatBuffer.put(v8)
        floatBuffer.put(v9)

        size += 10
        return this
    }
}

class IntBufferObject(id: Int, target: Int, usage: Int, blockBinding: Int?, maxSize: Long) : BufferObject(id, target, usage, blockBinding, maxSize)
{
    private var intBuffer: IntBuffer = byteBuffer.asIntBuffer()

    override fun hasCapacityfor (elements: Int): Boolean = intBuffer.position() + elements < intBuffer.capacity()

    override fun setTypeBuffer()
    {
        intBuffer = byteBuffer.asIntBuffer()
    }

    override fun flipTypeBuffer()
    {
        intBuffer.flip()
    }

    fun put(value: Int): IntBufferObject
    {
        intBuffer.put(value)
        size += 1
        return this
    }

    fun put(vararg values: Int): IntBufferObject
    {
        if (!hasCapacityfor (values.size))
            growSize()

        for (v in values)
            intBuffer.put(v)

        size += values.size
        return this
    }

    fun put(v01: Int, v02: Int, v03: Int, v04: Int, v05: Int, v06: Int): IntBufferObject
    {
        if (!hasCapacityfor (6))
            growSize()

        intBuffer.put(v01)
        intBuffer.put(v02)
        intBuffer.put(v03)
        intBuffer.put(v04)
        intBuffer.put(v05)
        intBuffer.put(v06)

        size += 6
        return this
    }
}

class ByteBufferObject(id: Int, target: Int, usage: Int, blockBinding: Int?, maxSize: Long) : BufferObject(id, target, usage, blockBinding, maxSize)
{
    override fun hasCapacityfor (elements: Int): Boolean = byteBuffer.position() + elements < byteBuffer.capacity()

    override fun setTypeBuffer() { }

    override fun flipTypeBuffer() { }

    fun put(value: Byte): ByteBufferObject
    {
        byteBuffer.put(value)
        size += 1
        return this
    }
}
