package no.njoh.pulseengine.core.graphics.gpu.buffer

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase
import org.lwjgl.opengl.GL15.*

class StaticBufferObject(
    val id: Int,
    private val target: Int,
    private val blockBinding: Int?
) {
    fun bind()
    {
        glBindBuffer(target, id)
        if (blockBinding != null)
            glBindBufferBase(target, blockBinding, id)
    }

    fun release() = glBindBuffer(target, 0)
    fun destroy() = glDeleteBuffers(id)

    companion object
    {
        fun createArrayBuffer(data: FloatArray, target: Int = GL_ARRAY_BUFFER, blockBinding: Int? = null): StaticBufferObject
        {
            val id = glGenBuffers()
            glBindBuffer(target, id)
            glBufferData(target, data, GL_STATIC_DRAW)
            glBindBuffer(target, 0)
            return StaticBufferObject(id, target, blockBinding)
        }

        fun createArrayBuffer(data: ByteArray, target: Int = GL_ARRAY_BUFFER, blockBinding: Int? = null): StaticBufferObject
        {
            val id = glGenBuffers()
            val buffer = BufferUtils.createByteBuffer(data.size)
            buffer.put(data).flip()

            glBindBuffer(target, id)
            glBufferData(target, buffer, GL_STATIC_DRAW)
            glBindBuffer(target, 0)
            return StaticBufferObject(id, target, blockBinding)
        }

        fun createElementArrayBuffer(data: IntArray, target: Int = GL_ELEMENT_ARRAY_BUFFER, blockBinding: Int? = null): StaticBufferObject
        {
            val id = glGenBuffers()
            glBindBuffer(target, id)
            glBufferData(target, data, GL_STATIC_DRAW)
            glBindBuffer(target, 0)
            return StaticBufferObject(id, target, blockBinding)
        }

        fun createQuadVertexArrayBuffer() = createArrayBuffer(
            // For GL_TRIANGLE_STRIP with CCW front face
            floatArrayOf(
                0f, 0f, // Bottom-left
                1f, 0f, // Bottom-right
                0f, 1f, // Top-left
                1f, 1f, // Top-right
            )
        )

        fun createFullscreenUvTriangleArrayBuffer() = createArrayBuffer(
            // For GL_TRIANGLE_STRIP with CCW front face
            floatArrayOf(
                -1f, -1f, 0f, 0f,
                 3f, -1f, 2f, 0f,
                -1f,  3f, 0f, 2f
            )
        )
    }
}