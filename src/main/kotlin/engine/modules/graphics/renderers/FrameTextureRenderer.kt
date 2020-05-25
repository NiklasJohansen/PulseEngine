package engine.modules.graphics.renderers

import engine.data.Texture
import engine.modules.graphics.ShaderProgram
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30.*

class FrameTextureRenderer
{
    private var vaoId = 0
    private var vboId = 0
    private lateinit var program: ShaderProgram

    fun init(shaderProgram: ShaderProgram)
    {
        this.program = shaderProgram

        val verticesBuffer = BufferUtils.createFloatBuffer(vertices.size)
        verticesBuffer.put(vertices)
        verticesBuffer.flip()

        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)

        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW)

        program.use()
        program.defineVertexAttributeArray("position", 2, GL_FLOAT, 4 * FLOAT_BYTES, 0)
        program.defineVertexAttributeArray("texCoord", 2, GL_FLOAT, 4 * FLOAT_BYTES, 2 * FLOAT_BYTES)

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
    }

    fun render(vararg texture: Texture)
    {
        glBindVertexArray(vaoId)
        glEnableVertexAttribArray(0)

        program.use()

        for((i, tex) in texture.withIndex())
        {
            glActiveTexture(i)
            glBindTexture(GL_TEXTURE_2D, tex.id)
        }

        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT)

        for(i in texture.indices)
        {
            glActiveTexture(i)
            glBindTexture(GL_TEXTURE_2D, 0)
        }

        glDisableVertexAttribArray(0)
        glBindVertexArray(0)
        glActiveTexture(0)
    }

    fun cleanUp()
    {
        glDisableVertexAttribArray(0)

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
    }
}