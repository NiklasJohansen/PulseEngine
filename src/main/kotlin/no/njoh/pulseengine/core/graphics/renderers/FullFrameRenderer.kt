package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30.*

class FullFrameRenderer(val program: ShaderProgram)
{
    private var vaoId = -1
    private var vboId = -1
    private var initialized = false

    fun init()
    {
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)

        if (!initialized)
        {
            vboId = glGenBuffers()
            glBindBuffer(GL_ARRAY_BUFFER, vboId)
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW)
            initialized = true
        }
        else glBindBuffer(GL_ARRAY_BUFFER, vboId)

        program.bind()
        VertexAttributeLayout()
            .withAttribute("position", 2, GL_FLOAT)
            .withAttribute("texCoord", 2, GL_FLOAT)
            .bind(program)
    }

    fun drawTexture(texture: RenderTexture)
    {
        program.bind()
        program.setUniformSampler("tex", texture)
        draw()
    }

    fun draw()
    {
        glBindVertexArray(vaoId)
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT)
    }

    fun destroy()
    {
        // Delete the VBO
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glDeleteBuffers(vboId)

        // Delete the VAO
        glBindVertexArray(0)
        glDeleteVertexArrays(vaoId)
    }

    companion object
    {
        private const val FLOAT_BYTES = java.lang.Float.BYTES
        private const val VERTEX_COUNT = 6
        private val vertices = floatArrayOf(
            -1f, -1f, 0f, 0f,  // v1 (top-left)
            -1f,  1f, 0f, 1f,  // v2 (bottom-left)
             1f,  1f, 1f, 1f,  // v3 (bottom-right)
             1f,  1f, 1f, 1f,  // v3 (bottom-right)
             1f, -1f, 1f, 0f,  // v4 (top-right)
            -1f, -1f, 0f, 0f   // v1 (top-left)
        )
        private val verticesBuffer = BufferUtils.createFloatBuffer(vertices.size).put(vertices).also { it.flip() }
    }
}