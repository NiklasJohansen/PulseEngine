package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureHandle
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30.*

class FrameTextureRenderer(private val program: ShaderProgram)
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
        program.setVertexAttributeLayout("position", 2, GL_FLOAT, 4 * FLOAT_BYTES, 0L)
        program.setVertexAttributeLayout("texCoord", 2, GL_FLOAT, 4 * FLOAT_BYTES, 2L * FLOAT_BYTES)

        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    fun render(texture: Texture)
    {
        glBindVertexArray(vaoId)

        program.bind()

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, texture.handle.textureIndex)

        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT)

        glBindTexture(GL_TEXTURE_2D, 0)
        glBindVertexArray(0)
        glActiveTexture(GL_TEXTURE0)
    }

    fun render(vararg textures: Texture)
    {
        glBindVertexArray(vaoId)

        var i = 0
        while (i < textures.size)
        {
            glActiveTexture(GL_TEXTURE0 + i)
            glBindTexture(GL_TEXTURE_2D, textures[i++].handle.textureIndex)
        }

        program.bind()
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT)

        glBindVertexArray(0)
    }

    fun render(textureHandles: List<TextureHandle>)
    {
        glBindVertexArray(vaoId)

        var i = 0
        while (i < textureHandles.size)
        {
            glActiveTexture(GL_TEXTURE0 + i)
            glBindTexture(GL_TEXTURE_2D, textureHandles[i++].textureIndex)
        }

        program.bind()
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT)

        glBindVertexArray(0)
    }

    fun cleanUp()
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
        private val verticesBuffer = BufferUtils
            .createFloatBuffer(vertices.size)
            .also {
                it.put(vertices)
                it.flip()
            }
    }
}