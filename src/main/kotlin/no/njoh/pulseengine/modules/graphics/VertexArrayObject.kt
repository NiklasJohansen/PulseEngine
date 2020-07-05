package no.njoh.pulseengine.modules.graphics


import org.lwjgl.opengl.GL30.*

class VertexArrayObject(val id: Int)
{
    fun bind() = this.also { glBindVertexArray(id) }
    fun release() = this.also { glBindVertexArray(0) }
    fun delete()
    {
        release()
        glDeleteVertexArrays(id)
    }

    companion object
    {
        fun createAndBind(): VertexArrayObject
        {
            val id = glGenVertexArrays()
            glBindVertexArray(id)
            return VertexArrayObject(id)
        }
    }
}