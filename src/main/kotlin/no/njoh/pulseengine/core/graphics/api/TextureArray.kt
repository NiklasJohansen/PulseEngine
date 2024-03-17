package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap
import org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.ARBTextureStorage.glTexStorage3D
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.glTexSubImage3D
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture

class TextureArray(
    val samplerIndex: Int,
    val textureSize: Int,
    val maxCapacity: Int,
    val textureFormat: TextureFormat,
    val textureFilter: TextureFilter,
    val mipLevels: Int
) {
    private var textureArrayId = -1
    var size = 0
        private set

    private fun init()
    {
        val (minFilter, magFilter) = when (textureFilter)
        {
            LINEAR -> Pair(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR)
            NEAREST -> Pair(GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST)
        }

        textureArrayId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId)
        glTexStorage3D(GL_TEXTURE_2D_ARRAY, mipLevels, textureFormat.value, textureSize, textureSize, maxCapacity)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, minFilter)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, magFilter)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)
    }

    fun upload(texture: Texture)
    {
        check(texture.width <= textureSize) { "Texture width (${texture.width} px) cannot be larger than $textureSize px" }
        check(texture.height <= textureSize) { "Texture width (${texture.height} px) cannot be larger than $textureSize px" }

        if (textureArrayId == -1)
            init()

        val texIndex = size++
        if (texIndex >= maxCapacity)
            throw RuntimeException("Texture array with capacity: $maxCapacity is full!")

        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId)

        if (textureFormat == RGBA8 && texture.pixelsLDR != null)
        {
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, texIndex, texture.width, texture.height, 1, GL_RGBA, GL_UNSIGNED_BYTE, texture.pixelsLDR!!)
        }
        else if (texture.pixelsHDR != null)
        {
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, texIndex, texture.width, texture.height, 1, GL_RGBA, GL_FLOAT, texture.pixelsHDR!!)
        }

        glGenerateMipmap(GL_TEXTURE_2D_ARRAY)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)

        val u = texture.width / textureSize.toFloat()
        val v = texture.height / textureSize.toFloat()
        val handle = TextureHandle.create(samplerIndex, texIndex)

        texture.finalize(handle = handle, isBindless = true, uMin = 0.0f, vMin = 0.0f, uMax = u, vMax = v)
    }

    fun isFull() = size >= maxCapacity

    fun delete(texture: Texture)
    {
        // TODO: Fix deleting/freeing of texture slots
        // glDeleteTextures(texture.id)
    }

    fun bind(program: ShaderProgram)
    {
        glActiveTexture(GL_TEXTURE0 + samplerIndex)
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId)
        program.setUniform(textureArrayNames[samplerIndex], samplerIndex)
    }

    fun cleanUp() = glDeleteTextures(textureArrayId)

    override fun toString(): String = "slot=$samplerIndex, maxSize=${textureSize}px, capacity=($size/$maxCapacity), format=$textureFormat, filter=$textureFilter, mips=$mipLevels"

    companion object
    {
        private val textureArrayNames = Array(64) { "textureArrays[$it]" }
    }
}