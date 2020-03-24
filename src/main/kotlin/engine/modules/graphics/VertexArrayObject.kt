package engine.modules.graphics


import org.lwjgl.opengl.GL30.*

class VertexArrayObject(val id: Int)
{
    fun bind() = this.also { glBindVertexArray(id) }

    fun delete() = glDeleteVertexArrays(id)

    companion object
    {
        fun create(): VertexArrayObject
        {
            val id = glGenVertexArrays()
            glBindVertexArray(id)
            return VertexArrayObject(id)
        }
    }
}