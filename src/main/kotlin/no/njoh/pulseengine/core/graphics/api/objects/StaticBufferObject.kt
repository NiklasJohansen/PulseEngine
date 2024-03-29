package no.njoh.pulseengine.core.graphics.api.objects

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
    fun delete() = glDeleteBuffers(id)

    companion object
    {
        fun createBuffer(
            data: FloatArray,
            target: Int = GL_ARRAY_BUFFER,
            blockBinding: Int? = null
        ): StaticBufferObject {
            val id = glGenBuffers()
            glBindBuffer(target, id)
            glBufferData(target, data, GL_STATIC_DRAW)
            glBindBuffer(target, 0)
            return StaticBufferObject(id, target, blockBinding)
        }

        val QUAD_VERTICES = floatArrayOf(
            0f, 0f, // Top-left vertex
            1f, 0f, // Top-right vertex
            0f, 1f, // Bottom-left vertex
            1f, 1f  // Bottom-right vertex
        )
    }
}