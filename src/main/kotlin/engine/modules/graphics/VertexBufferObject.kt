package engine.modules.graphics

import org.lwjgl.opengl.GL15.*
import org.lwjgl.system.MemoryUtil
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

sealed class VertexBufferObject(
    private val id: Int,
    private val target: Int,
    private val usage: Int,
    private var maxSize: Long
) {
    protected var byteBuffer: ByteBuffer = MemoryUtil.memAlloc(maxSize.toInt())
    protected var size: Int = 0
    private var countToDraw = 0

    fun bind() = this.also { glBindBuffer(target, id) }
    fun release() = this.also { glBindBuffer(target, 0) }

    fun growSize(factor: Float = 2f)
    {
        changeSize((maxSize * factor).toLong())
    }

    private fun changeSize(size: Long)
    {
        println("Changing buffer object capacity from $maxSize to: $size bytes (${"${size/1_000_000f}".format("%.2f")} MB)")
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
    abstract fun hasCapacityFor(elements: Int = 0): Boolean

    companion object
    {
        inline fun <reified T: VertexBufferObject> createAndBind(size: Long, usage: Int = GL_DYNAMIC_DRAW): T
             = createAndBindBuffer(size, usage, GL_ARRAY_BUFFER)

         fun createAndBindElementBuffer(size: Long, usage: Int = GL_DYNAMIC_DRAW): IntBufferObject
            = createAndBindBuffer(size, usage, GL_ELEMENT_ARRAY_BUFFER)

        inline fun <reified T> createAndBindBuffer(size: Long, usage: Int, target: Int): T
        {
            val id = glGenBuffers()

            glBindBuffer(target, id)
            glBufferData(target, size, usage)

            return when(T::class)
            {
                FloatBufferObject::class -> FloatBufferObject(id, target, usage, size) as T
                IntBufferObject::class -> IntBufferObject(id, target, usage, size) as T
                ByteBufferObject::class -> ByteBufferObject(id, target, usage, size) as T
                else -> throw IllegalArgumentException("type: ${T::class.simpleName} not supported")
            }
        }
    }
}

class FloatBufferObject(id: Int, target: Int, usage: Int, maxSize: Long) : VertexBufferObject(id, target, usage, maxSize)
{
    private var floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()

    override fun hasCapacityFor(elements: Int): Boolean = floatBuffer.position() + elements <= floatBuffer.capacity()

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

    fun put(vararg values: Float): FloatBufferObject
    {
        if (!hasCapacityFor(values.size))
            growSize()

        for (v in values)
            floatBuffer.put(v)

        size += values.size
        return this
    }
}

class IntBufferObject(id: Int, target: Int, usage: Int, maxSize: Long) : VertexBufferObject(id, target, usage, maxSize)
{
    private var intBuffer: IntBuffer = byteBuffer.asIntBuffer()

    override fun hasCapacityFor(elements: Int): Boolean = intBuffer.position() + elements < intBuffer.capacity()

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
        if (!hasCapacityFor(values.size))
            growSize()

        for (v in values)
            intBuffer.put(v)

        size += values.size
        return this
    }

    fun put(v01: Int, v02: Int, v03: Int, v04: Int, v05: Int, v06: Int): IntBufferObject
    {
        if (!hasCapacityFor(6))
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

class ByteBufferObject(id: Int, target: Int, usage: Int, maxSize: Long) : VertexBufferObject(id, target, usage, maxSize)
{
    override fun hasCapacityFor(elements: Int): Boolean = byteBuffer.position() + elements < byteBuffer.capacity()

    override fun setTypeBuffer() { }

    override fun flipTypeBuffer() { }

    fun put(value: Byte): ByteBufferObject
    {
        byteBuffer.put(value)
        size += 1
        return this
    }
}
