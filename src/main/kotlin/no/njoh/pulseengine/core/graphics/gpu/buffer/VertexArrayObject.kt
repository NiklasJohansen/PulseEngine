package no.njoh.pulseengine.core.graphics.gpu.buffer

import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import org.lwjgl.opengl.GL30.*

class VertexArrayObject(val id: Int)
{
    private val contextGeneration = GlCapabilities.contextGeneration
    private var destroyed = false

    fun bind() = this.also { glBindVertexArray(id) }
    fun release() = this.also { glBindVertexArray(0) }
    fun destroy()
    {
        if (destroyed) return

        destroyed = true
        if (contextGeneration == GlCapabilities.contextGeneration)
        {
            release()
            glDeleteVertexArrays(id)
        }
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