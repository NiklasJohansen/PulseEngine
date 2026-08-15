package no.njoh.pulseengine.core.graphics.gpu.texture

import gnu.trove.list.array.TIntArrayList
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import org.lwjgl.opengl.ARBClearTexture.glClearTexImage
import org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap
import org.lwjgl.opengl.ARBTextureStorage.glTexStorage3D
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_TEXTURE_BASE_LEVEL
import org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL
import org.lwjgl.opengl.GL12.glTexImage3D
import org.lwjgl.opengl.GL12.glTexSubImage3D
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32.glFramebufferTexture
import java.nio.ByteBuffer
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

class TextureArray(
    val samplerIndex: Int,
    val textureSize: Int,
    val maxCapacity: Int,
    val format: TextureFormat,
    val filter: TextureFilter,
    val anisotropy: TextureAnisotropy,
    val wrapping: TextureWrapping,
    val maxMipLevels: Int
) {
    var id  = -1; private set
    var size = 0; private set

    val mipLevels = if (textureSize > 0) min(maxMipLevels, floor(log2(textureSize.toDouble())).toInt() + 1) else 1

    private var freeSlots = TIntArrayList()

    fun init()
    {
        id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D_ARRAY, id)
        val storageWidth = max(textureSize, 1)
        val storageDepth = max(maxCapacity, 1)

        if (GlCapabilities.immutableTextureStorage)
        {
            glTexStorage3D(GL_TEXTURE_2D_ARRAY, mipLevels, format.internalFormat, storageWidth, storageWidth, storageDepth)
        }
        else
        {
            for (level in 0 until mipLevels)
            {
                val levelSize = max(storageWidth shr level, 1)
                glTexImage3D(GL_TEXTURE_2D_ARRAY, level, format.internalFormat, levelSize, levelSize, storageDepth, 0, format.pixelFormat, format.type, null as ByteBuffer?)
            }
        }

        if (GlCapabilities.clearTexture)
        {
            glClearTexImage(id, 0, format.pixelFormat, format.type, null as ByteBuffer?)
        }
        else
        {
            clearBaseLevel()
        }

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_BASE_LEVEL, 0)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_LEVEL, mipLevels - 1)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, wrapping.value)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, wrapping.value)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, filter.minValue)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, filter.magValue)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)
    }

    fun upload(texture: Texture)
    {
        check(texture.width <= textureSize) { "Texture width (${texture.width} px) cannot be larger than $textureSize px" }
        check(texture.height <= textureSize) { "Texture height (${texture.height} px) cannot be larger than $textureSize px" }

        if (id == -1)
            init()

        val texIndex = when
        {
            !freeSlots.isEmpty -> freeSlots.removeAt(freeSlots.size() - 1)
            size >= maxCapacity -> throw RuntimeException("Texture array with capacity: $maxCapacity is full!")
            else -> size++
        }

        glBindTexture(GL_TEXTURE_2D_ARRAY, id)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)

        if (texture.pixelsLDR != null)
        {
            check(format.type == GL_UNSIGNED_BYTE) { "Pixel buffer type: ${texture.pixelsLDR!!::class.simpleName} doesn't match texture format: $format" }
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, texIndex, texture.width, texture.height, 1, format.pixelFormat, format.type, texture.pixelsLDR!!)
        }
        else if (texture.pixelsHDR != null)
        {
            check(format.type == GL_FLOAT) { "Pixel buffer type: ${texture.pixelsHDR!!::class.simpleName} doesn't match texture format: $format" }
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, texIndex, texture.width, texture.height, 1, format.pixelFormat, format.type, texture.pixelsHDR!!)
        }

        if (mipLevels > 1 && (texture.pixelsHDR != null || texture.pixelsLDR != null))
            glGenerateMipmap(GL_TEXTURE_2D_ARRAY) // TODO: This generate mipmaps for the whole array on every upload

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)

        val u = texture.width / textureSize.toFloat()
        val v = texture.height / textureSize.toFloat()
        val handle = TextureHandle.create(samplerIndex, texIndex)

        texture.onUploaded(handle, uMin = 0.0f, vMin = 0.0f, uMax = u, vMax = v)
    }

    fun isFull() = (size >= maxCapacity && freeSlots.isEmpty)

    fun delete(texture: Texture)
    {
        val texIndex = texture.handle.textureIndex
        if (texture.handle.samplerIndex == samplerIndex && !freeSlots.contains(texIndex))
            freeSlots.add(texIndex)
    }

    fun destroy()
    {
        if (id != -1)
            glDeleteTextures(id)
        id = -1
        size = 0
        freeSlots.clear()
    }

    /**
     * Clears every array layer without relying on the OpenGL 4.4 clear-texture entry points.
     */
    private fun clearBaseLevel()
    {
        val previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val clearFramebuffer = glGenFramebuffers()
        try
        {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, clearFramebuffer)
            glFramebufferTexture(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, id, 0)
            check(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Failed to create temporary framebuffer for clearing texture array #$id" }
            when
            {
                !format.isIntegerFormat        -> glClearBufferfv(GL_COLOR,  0, ZERO_FLOAT_COLOR)
                format.type == GL_UNSIGNED_INT -> glClearBufferuiv(GL_COLOR, 0, ZERO_INT_COLOR)
                else                           -> glClearBufferiv(GL_COLOR,  0, ZERO_INT_COLOR)
            }
        }
        finally
        {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
            glDeleteFramebuffers(clearFramebuffer)
        }
    }

    override fun toString(): String = "slot=$samplerIndex, maxSize=${textureSize}px, capacity=($size/$maxCapacity), format=$format, filter=$filter, anisotropy=$anisotropy, wrapping=$wrapping, mips=$mipLevels"

    companion object
    {
        private val ZERO_FLOAT_COLOR = floatArrayOf(0f, 0f, 0f, 0f)
        private val ZERO_INT_COLOR = intArrayOf(0, 0, 0, 0)
    }
}
