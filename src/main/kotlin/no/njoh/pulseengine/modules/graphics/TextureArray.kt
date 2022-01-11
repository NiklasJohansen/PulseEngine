package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.assets.Texture
import org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap
import org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.ARBTextureStorage.glTexStorage3D
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL12.glTexSubImage3D
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture

class TextureArray(
    private val maxTextureWidth: Int,
    private val maxTextureHeight: Int,
    private val capacity: Int,
    private val mipMapLevels: Int = 4
) {
    private var uploadedTextureCount = 0
    private var textureArrayId = -1

    private fun init()
    {
        textureArrayId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId)
        glTexStorage3D(GL_TEXTURE_2D_ARRAY, mipMapLevels, GL_RGBA8, maxTextureWidth, maxTextureHeight, capacity)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)
    }

    fun upload(texture: Texture)
    {
        check(texture.width <= maxTextureWidth) { "Texture width (${texture.width} px) cannot be larger than $maxTextureWidth px" }
        check(texture.height <= maxTextureHeight) { "Texture width (${texture.height} px) cannot be larger than $maxTextureHeight px" }

        if (textureArrayId == -1)
            init()

        val index = uploadedTextureCount++
        if (index >= capacity)
            throw RuntimeException("Texture array with capacity: $capacity is full!")

        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId)
        glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, index, texture.width, texture.height, 1, texture.format, GL_UNSIGNED_BYTE, texture.textureData!!)
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)

        val u = texture.width / maxTextureWidth.toFloat()
        val v = texture.height / maxTextureHeight.toFloat()
        texture.finalize(index, 0.0f, 0.0f, u, v)
    }

    fun bind(textureUnit: Int = 0)
    {
        glActiveTexture(GL_TEXTURE0 + textureUnit)
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId)
    }

    fun cleanup() = glDeleteTextures(textureArrayId)
}