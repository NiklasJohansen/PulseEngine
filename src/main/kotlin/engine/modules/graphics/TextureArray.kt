package engine.modules.graphics

import engine.data.Texture
import org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.ARBTextureStorage
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL12.glTexSubImage3D
import java.lang.RuntimeException

class TextureArray(
    private val maxTextureWidth: Int,
    private val maxTextureHeight: Int,
    private val capacity: Int
) {
    private var uploadedTextureCount = 0
    private var textureArrayId = -1

    private fun init()
    {
        textureArrayId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId)
        ARBTextureStorage.glTexStorage3D(GL_TEXTURE_2D_ARRAY, 1, GL_RGBA8, maxTextureWidth, maxTextureHeight, capacity)

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        glTexParameterf(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR.toFloat())
        glTexParameterf(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR.toFloat())

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)
    }

    fun upload(texture: Texture)
    {
        check(texture.width <= maxTextureWidth) { "Texture width (${texture.width} px) cannot be larger than $maxTextureWidth px" }
        check(texture.height <= maxTextureHeight) { "Texture width (${texture.height} px) cannot be larger than $maxTextureHeight px" }

        if(textureArrayId == -1)
            init()

        val index = uploadedTextureCount++
        if(index >= capacity)
            throw RuntimeException("Texture array with capacity: $capacity is full!")

        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId)
        glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, index, texture.width, texture.height, 1, texture.format, GL_UNSIGNED_BYTE, texture.textureData!!)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)

        val u = texture.width / maxTextureWidth.toFloat()
        val v = texture.height / maxTextureHeight.toFloat()
        texture.finalize(index, u, v)
    }

    fun bind()
    {
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId)
    }

    fun cleanup()
    {
        glDeleteTextures(textureArrayId)
    }
}