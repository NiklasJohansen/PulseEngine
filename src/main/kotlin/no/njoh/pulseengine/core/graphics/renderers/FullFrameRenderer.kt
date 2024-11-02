package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureHandle
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30.*
import kotlin.math.min

class FullFrameRenderer(private val program: ShaderProgram)
{
    private var vaoId = -1
    private var vboId = -1
    private var initialized = false
    private var textureHandles = Array(7) { TextureHandle.NONE }

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
    }

    fun drawTexture(texture: Texture)
    {
        drawTextureHandle(texture.handle)
    }

    fun drawTextureHandle(handle: TextureHandle)
    {
        textureHandles[0] = handle
        drawTextureHandles(textureHandles, 1)
    }

    fun drawTextures(t0: Texture, t1: Texture)
    {
        textureHandles[0] = t0.handle
        textureHandles[1] = t1.handle
        drawTextureHandles(textureHandles, 2)
    }

    fun drawTextures(t0: Texture, t1: Texture, t2: Texture)
    {
        textureHandles[0] = t0.handle
        textureHandles[1] = t1.handle
        textureHandles[2] = t2.handle
        drawTextureHandles(textureHandles, 3)
    }

    fun drawTextures(t0: Texture, t1: Texture, t2: Texture, t3: Texture)
    {
        textureHandles[0] = t0.handle
        textureHandles[1] = t1.handle
        textureHandles[2] = t2.handle
        textureHandles[3] = t3.handle
        drawTextureHandles(textureHandles, 4)
    }

    fun drawTextures(t0: Texture, t1: Texture, t2: Texture, t3: Texture, t4: Texture)
    {
        textureHandles[0] = t0.handle
        textureHandles[1] = t1.handle
        textureHandles[2] = t2.handle
        textureHandles[3] = t3.handle
        textureHandles[4] = t4.handle
        drawTextureHandles(textureHandles, 5)
    }

    fun drawTextures(t0: Texture, t1: Texture, t2: Texture, t3: Texture, t4: Texture, t5: Texture)
    {
        textureHandles[0] = t0.handle
        textureHandles[1] = t1.handle
        textureHandles[2] = t2.handle
        textureHandles[3] = t3.handle
        textureHandles[4] = t4.handle
        textureHandles[5] = t5.handle
        drawTextureHandles(textureHandles, 6)
    }

    fun drawTextures(t0: Texture, t1: Texture, t2: Texture, t3: Texture, t4: Texture, t5: Texture, t6: Texture)
    {
        textureHandles[0] = t0.handle
        textureHandles[1] = t1.handle
        textureHandles[2] = t2.handle
        textureHandles[3] = t3.handle
        textureHandles[4] = t4.handle
        textureHandles[5] = t5.handle
        textureHandles[6] = t6.handle
        drawTextureHandles(textureHandles, 7)
    }

    fun drawTextures(textures: List<Texture>)
    {
        glBindVertexArray(vaoId)
        for (i in 0 until textures.size)
        {
            glActiveTexture(GL_TEXTURE0 + i)
            glBindTexture(GL_TEXTURE_2D, textures[i].handle.textureIndex)
        }
        program.bind()
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT)
    }

    fun drawTextureHandles(textureHandles: Array<TextureHandle>, count: Int = textureHandles.size)
    {
        glBindVertexArray(vaoId)
        for (i in 0 until min(count, textureHandles.size))
        {
            glActiveTexture(GL_TEXTURE0 + i)
            glBindTexture(GL_TEXTURE_2D, textureHandles[i].textureIndex)
        }
        program.bind()
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