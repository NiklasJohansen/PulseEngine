package no.njoh.pulseengine.modules.graphics.objects

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
        fun createAndBindBuffer(
            data: FloatArray,
            target: Int = GL_ARRAY_BUFFER,
            blockBinding: Int? = null
        ): StaticBufferObject {
            val id = glGenBuffers()
            glBindBuffer(target, id)
            glBufferData(target, data, GL_STATIC_DRAW)
            return StaticBufferObject(id, target, blockBinding)
        }
    }
}