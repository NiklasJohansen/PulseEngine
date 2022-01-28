package no.njoh.pulseengine.modules.graphics.objects

import no.njoh.pulseengine.util.Logger
import org.lwjgl.opengl.ARBUniformBufferObject.GL_UNIFORM_BUFFER
import org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase
import org.lwjgl.opengl.GL30.glMapBufferRange
import org.lwjgl.opengl.GL44.*
import org.lwjgl.system.MemoryUtil.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

abstract class PersistentBufferObject(
    val id: Int,
    private val target: Int,
    private val blockBinding: Int?,
    private val numBuffers: Int,
    private val bufferSize: Int
) {
    var count = 0
    var bufferIndex = 0
    private var countToDraw = 0
    private var bufferIndexToDraw = 0
    private val syncObjects = Array(numBuffers) { SyncObj() }

    fun bind()
    {
        glBindBuffer(target, id)
        if (blockBinding != null)
            glBindBufferBase(target, blockBinding, id)
    }

    fun release()
    {
        glBindBuffer(target, 0)
    }

    fun delete()
    {
        onDelete()
        glDeleteBuffers(id)
    }

    fun submit()
    {
        if (count == 0)
            return

        val syncObj = syncObjects[bufferIndex]
        syncObj.waitSync()
        onFillBuffer(bufferIndex)
        syncObj.lockSync()

        bufferIndexToDraw = bufferIndex
        bufferIndex = (bufferIndex + 1) % numBuffers
        countToDraw = count
        count = 0
    }

    /**
     * @param mode specifies what kind of primitives to render.
     * @param stride for VBO stride is attributes per vertex. For EBO stride is number of indices per element.
     */
    fun draw(mode: Int = GL_TRIANGLES, stride: Int)
    {
        if (countToDraw == 0)
            return

        val vertexCount = countToDraw / stride
        val firstVertexPosition = bufferIndexToDraw * (bufferSize / stride)

        when (target)
        {
            GL_ARRAY_BUFFER -> glDrawArrays(mode, firstVertexPosition, vertexCount)
            GL_ELEMENT_ARRAY_BUFFER -> glDrawElementsBaseVertex(mode, countToDraw, GL_UNSIGNED_INT, 0, 4 * firstVertexPosition)
            else -> Logger.error("Unsupported draw mode: $mode")
        }

        countToDraw = 0
    }

    protected abstract fun onDelete()
    protected abstract fun onFillBuffer(bufferIndex: Int)

    companion object
    {
        inline fun <reified T: PersistentBufferObject> createArrayBuffer(sizeInBytes: Long, numBuffers: Int = 1): T =
            createBuffer(sizeInBytes, GL_ARRAY_BUFFER, numBuffers, null)

        inline fun <reified T: PersistentBufferObject> createUniformBuffer(sizeInBytes: Long, numBuffers: Int = 1, blockBinding: Int): T =
            createBuffer(sizeInBytes, GL_UNIFORM_BUFFER, numBuffers, blockBinding)

        inline fun <reified T: PersistentBufferObject> createShaderStorageBuffer(sizeInBytes: Long, numBuffers: Int = 1, blockBinding: Int): T =
            createBuffer(sizeInBytes, GL_SHADER_STORAGE_BUFFER, numBuffers, blockBinding)

        fun createElementBuffer(sizeInBytes: Long, numBuffers: Int = 1): PersistentIntBufferObject =
            createBuffer(sizeInBytes, GL_ELEMENT_ARRAY_BUFFER, numBuffers, null)

        inline fun <reified T: PersistentBufferObject> createBuffer(
            sizeInBytes: Long,
            target: Int,
            numBuffers: Int = 1,
            blockBinding: Int? = null
        ): T {
            val bufferSize = numBuffers * sizeInBytes
            val flags = GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT
            val id = glGenBuffers()
            glBindBuffer(target, id)
            glBufferStorage(target, bufferSize, flags)
            val persistentBuffer = glMapBufferRange(target, 0, bufferSize, flags)!!
            glBindBuffer(target, 0)

            return when (T::class)
            {
                PersistentFloatBufferObject::class ->
                    PersistentFloatBufferObject(id, target, blockBinding, persistentBuffer, numBuffers, (sizeInBytes / 4).toInt()) as T
                PersistentIntBufferObject::class ->
                    PersistentIntBufferObject(id, target, blockBinding, persistentBuffer, numBuffers, (sizeInBytes / 4).toInt()) as T
                else -> throw IllegalArgumentException("type: ${T::class.simpleName} not supported")
            }
        }
    }
}

class PersistentFloatBufferObject(
    id: Int,
    target: Int,
    blockBinding: Int?,
    persistentBuffer: ByteBuffer,
    numBuffers: Int,
    val bufferSize: Int
): PersistentBufferObject(id, target, blockBinding, numBuffers, bufferSize)
{
    @PublishedApi
    internal val backingBuffer = memAllocFloat(bufferSize)
    private val mappedBuffer = persistentBuffer.asFloatBuffer()

    override fun onDelete() = memFree(backingBuffer)

    override fun onFillBuffer(bufferIndex: Int)
    {
        val bufferOffset = bufferIndex * bufferSize
        backingBuffer.flip()
        mappedBuffer.limit(bufferOffset + bufferSize)
        mappedBuffer.position(bufferOffset)
        mappedBuffer.put(backingBuffer)
        mappedBuffer.flip()
        backingBuffer.clear()
    }

    inline fun fill(amount: Int, fillBuffer: FloatBuffer.() -> Unit)
    {
        if (count + amount > bufferSize)
            return

        fillBuffer(backingBuffer)

        count += amount
    }
}

class PersistentIntBufferObject(
    id: Int,
    target: Int,
    blockBinding: Int?,
    persistentBuffer: ByteBuffer,
    numBuffers: Int,
    val bufferSize: Int
): PersistentBufferObject(id, target, blockBinding, numBuffers, bufferSize)
{
    @PublishedApi
    internal val backingBuffer = memAllocInt(bufferSize)
    private val mappedBuffer = persistentBuffer.asIntBuffer()

    override fun onDelete() = memFree(backingBuffer)

    override fun onFillBuffer(bufferIndex: Int)
    {
        val bufferOffset = bufferIndex * bufferSize
        backingBuffer.flip()
        mappedBuffer.limit(bufferOffset + bufferSize)
        mappedBuffer.position(bufferOffset)
        mappedBuffer.put(backingBuffer)
        mappedBuffer.flip()
        backingBuffer.clear()
    }

    inline fun fill(amount: Int, fillBuffer: IntBuffer.() -> Unit)
    {
        if (count + amount > bufferSize)
            return

        fillBuffer(backingBuffer)

        count += amount
    }
}

