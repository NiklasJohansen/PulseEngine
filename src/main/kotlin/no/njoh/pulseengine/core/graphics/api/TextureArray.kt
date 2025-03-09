package no.njoh.pulseengine.core.graphics.api

import gnu.trove.list.array.TIntArrayList
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap
import org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.ARBTextureStorage.glTexStorage3D
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.glTexSubImage3D

class TextureArray(
    val samplerIndex: Int,
    val textureSize: Int,
    val maxCapacity: Int,
    val format: TextureFormat,
    val filter: TextureFilter,
    val wrapping: TextureWrapping,
    val mipLevels: Int
) {
    var id  = -1; private set
    var size = 0; private set

    private var freeSlots = TIntArrayList()

    fun init()
    {
        id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D_ARRAY, id)
        glTexStorage3D(GL_TEXTURE_2D_ARRAY, mipLevels, format.internalFormat, textureSize, textureSize, maxCapacity)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, wrapping.value)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, wrapping.value)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, filter.minValue)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, filter.magValue)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)
    }

    fun upload(texture: Texture)
    {
        check(texture.width <= textureSize) { "Texture width (${texture.width} px) cannot be larger than $textureSize px" }
        check(texture.height <= textureSize) { "Texture width (${texture.height} px) cannot be larger than $textureSize px" }

        if (id == -1)
            init()

        val texIndex = if (freeSlots.isEmpty) size++ else freeSlots.removeAt(freeSlots.size() - 1)
        if (texIndex >= maxCapacity)
            throw RuntimeException("Texture array with capacity: $maxCapacity is full!")

        glBindTexture(GL_TEXTURE_2D_ARRAY, id)

        if ((format == RGBA8 || format == SRGBA8) && texture.pixelsLDR != null)
        {
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, texIndex, texture.width, texture.height, 1, GL_RGBA, GL_UNSIGNED_BYTE, texture.pixelsLDR!!)
        }
        else if ((format == RGBA16F || format == RGBA32F) && texture.pixelsHDR != null)
        {
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, texIndex, texture.width, texture.height, 1, GL_RGBA, GL_FLOAT, texture.pixelsHDR!!)
        }
        else
        {
            Logger.error("Failed to upload texture to texture array. Unsupported texture format: $format")
            return
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
        val texIndex = texture.handle.textureIndex
        if (texture.handle.samplerIndex == samplerIndex && !freeSlots.contains(texIndex))
            freeSlots.add(texIndex)
    }

    fun destroy() = glDeleteTextures(id)

    override fun toString(): String = "slot=$samplerIndex, maxSize=${textureSize}px, capacity=($size/$maxCapacity), format=$format, filter=$filter, mips=$mipLevels"
}