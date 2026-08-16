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
    val textureArraySlot: Int,
    val textureSize: Int,
    val maxCapacity: Int,
    val format: TextureFormat,
    val filter: TextureFilter,
    val anisotropy: TextureAnisotropy,
    val wrapping: TextureWrapping,
    val maxMipLevels: Int
) {
    init
    {
        require(textureArraySlot in 0..MAX_TEXTURE_ARRAY_SLOT) { "Texture array slot: $textureArraySlot must be in the range 0..$MAX_TEXTURE_ARRAY_SLOT" }
        require(maxCapacity in 0..MAX_CAPACITY) { "Texture array capacity: $maxCapacity must be in the range 0..$MAX_CAPACITY" }
    }

    var id  = -1; private set
    var size = 0; private set

    val mipLevels = if (textureSize > 0) min(maxMipLevels, floor(log2(textureSize.toDouble())).toInt() + 1) else 1

    private val slotOwners = arrayOfNulls<Texture>(maxCapacity)
    private var freeSlots = TIntArrayList()
    private var mipmapsDirty = false

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

        val pixelsLDR = texture.pixelsLDR
        val pixelsHDR = texture.pixelsHDR
        if (pixelsLDR != null)
        {
            check(format.type == GL_UNSIGNED_BYTE) { "Pixel buffer type: ${pixelsLDR::class.simpleName} doesn't match texture format: $format" }
        }
        else if (pixelsHDR != null)
        {
            check(format.type == GL_FLOAT) { "Pixel buffer type: ${pixelsHDR::class.simpleName} doesn't match texture format: $format" }
        }

        if (id == -1)
            init()

        val layerIndex = when
        {
            !freeSlots.isEmpty -> freeSlots.removeAt(freeSlots.size() - 1)
            size >= maxCapacity -> throw RuntimeException("Texture array with capacity: $maxCapacity is full!")
            else -> size++
        }

        glBindTexture(GL_TEXTURE_2D_ARRAY, id)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)

        if (pixelsLDR != null)
        {
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layerIndex, texture.width, texture.height, 1, format.pixelFormat, format.type, pixelsLDR)
        }
        else if (pixelsHDR != null)
        {
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layerIndex, texture.width, texture.height, 1, format.pixelFormat, format.type, pixelsHDR)
        }

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)

        if (mipLevels > 1 && (pixelsHDR != null || pixelsLDR != null))
            mipmapsDirty = true

        val u = texture.width / textureSize.toFloat()
        val v = texture.height / textureSize.toFloat()
        val handle = TextureHandle.createArrayHandle(textureArraySlot, layerIndex)

        slotOwners[layerIndex] = texture
        texture.onUploaded(handle, uMin = 0.0f, vMin = 0.0f, uMax = u, vMax = v)
    }

    internal fun generatePendingMipmaps()
    {
        if (!mipmapsDirty)
            return

        glBindTexture(GL_TEXTURE_2D_ARRAY, id)
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)
        mipmapsDirty = false
    }

    fun isFull() = (size >= maxCapacity && freeSlots.isEmpty)

    fun delete(texture: Texture)
    {
        val handle = texture.handle
        if (!handle.isArrayTexture || handle.textureArraySlot != textureArraySlot)
            return

        val layerIndex = handle.textureArrayLayer
        if (slotOwners.getOrNull(layerIndex) !== texture)
            return

        slotOwners[layerIndex] = null
        freeSlots.add(layerIndex)
    }

    fun destroy()
    {
        if (id != -1)
            glDeleteTextures(id)
        id = -1
        size = 0
        mipmapsDirty = false
        freeSlots.clear()
        slotOwners.fill(null)
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

    override fun toString(): String = "slot=$textureArraySlot, maxSize=${textureSize}px, capacity=($size/$maxCapacity), format=$format, filter=$filter, anisotropy=$anisotropy, wrapping=$wrapping, mips=$mipLevels"

    companion object
    {
        private const val MAX_CAPACITY           = 32_768
        private const val MAX_TEXTURE_ARRAY_SLOT = 32_767

        private val ZERO_FLOAT_COLOR = floatArrayOf(0f, 0f, 0f, 0f)
        private val ZERO_INT_COLOR   = intArrayOf(0, 0, 0, 0)
    }
}
