package no.njoh.pulseengine.core.graphics.gpu.texture

import gnu.trove.list.array.TIntArrayList
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import org.lwjgl.opengl.ARBClearTexture.glClearTexImage
import org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap
import org.lwjgl.opengl.ARBTextureStorage.glTexStorage3D
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_TEXTURE_DEPTH
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
    val format: TextureFormat,
    val filter: TextureFilter,
    val anisotropy: TextureAnisotropy,
    val wrapping: TextureWrapping,
    val maxMipLevels: Int
) {
    init
    {
        require(textureArraySlot in 0..MAX_TEXTURE_ARRAY_SLOT) { "Texture array slot: $textureArraySlot must be in the range 0..$MAX_TEXTURE_ARRAY_SLOT" }
    }

    var id                = -1; private set
    var size              =  0; private set
    var allocatedCapacity =  0; private set

    val mipLevels = if (textureSize > 0) min(maxMipLevels, floor(log2(textureSize.toDouble())).toInt() + 1) else 1

    private var slotOwners = emptyArray<Texture?>()
    private var freeSlots = TIntArrayList()
    private var mipmapsDirty = false
    private val maximumCapacity get() = min(GlCapabilities.limits.maxArrayTextureLayers, MAX_HANDLE_LAYER_COUNT)

    fun init()
    {
        if (id != -1) return

        val initialCapacity = if (textureSize > 0) 1 else 0
        try
        {
            id = createStorage(max(initialCapacity, 1))
            allocatedCapacity = initialCapacity
            slotOwners = arrayOfNulls(initialCapacity)
        }
        catch (e: Exception)
        {
            throw TextureArrayAllocationException("Failed to allocate initial storage for texture array: $this", e)
        }
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
            else ->
            {
                ensureCapacity(size + 1)
                size++
            }
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

    fun generatePendingMipmaps()
    {
        if (!mipmapsDirty) return

        glBindTexture(GL_TEXTURE_2D_ARRAY, id)
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)
        mipmapsDirty = false
    }

    fun isFull() = size >= maximumCapacity && freeSlots.isEmpty

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
        allocatedCapacity = 0
        mipmapsDirty = false
        freeSlots.clear()
        slotOwners = emptyArray()
    }

    private fun ensureCapacity(requiredCapacity: Int)
    {
        if (requiredCapacity <= allocatedCapacity) return

        val newCapacity = calculateTextureArrayCapacity(requiredCapacity)
        val previousId = id
        val candidateId = try
        {
            createStorage(newCapacity)
        }
        catch (e: Exception)
        {
            throw TextureArrayAllocationException("Failed to grow texture array from $allocatedCapacity to $newCapacity layers: $this", e)
        }

        try
        {
            copyStorage(previousId, candidateId)
        }
        catch (e: Exception)
        {
            glDeleteTextures(candidateId)
            throw TextureArrayAllocationException("Failed to copy texture array while growing from $allocatedCapacity to $newCapacity layers: $this", e)
        }

        id = candidateId
        allocatedCapacity = newCapacity
        slotOwners = slotOwners.copyOf(newCapacity)
        glDeleteTextures(previousId)
    }

    private fun calculateTextureArrayCapacity(requiredCapacity: Int): Int
    {
        require(requiredCapacity <= maximumCapacity) { "Texture array requires $requiredCapacity layers, but this device supports at most $maximumCapacity" }

        var capacity = max(allocatedCapacity, 1)
        while (capacity < requiredCapacity)
            capacity = min(capacity * 2, maximumCapacity)

        return capacity
    }

    private fun createStorage(capacity: Int): Int
    {
        val textureId = glGenTextures()
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY)
        try
        {
            glBindTexture(GL_TEXTURE_2D_ARRAY, textureId)
            val storageWidth = max(textureSize, 1)

            if (GlCapabilities.immutableTextureStorage)
            {
                glTexStorage3D(GL_TEXTURE_2D_ARRAY, mipLevels, format.internalFormat, storageWidth, storageWidth, capacity)
            }
            else
            {
                for (level in 0 until mipLevels)
                {
                    val levelSize = max(storageWidth shr level, 1)
                    glTexImage3D(GL_TEXTURE_2D_ARRAY, level, format.internalFormat, levelSize, levelSize, capacity, 0, format.pixelFormat, format.type, null as ByteBuffer?)
                }
            }

            if (GlCapabilities.clearTexture)
            {
                glClearTexImage(textureId, 0, format.pixelFormat, format.type, null as ByteBuffer?)
            }
            else
            {
                clearBaseLevel(textureId)
            }

            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_BASE_LEVEL, 0)
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_LEVEL, mipLevels - 1)
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, wrapping.value)
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, wrapping.value)
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, filter.minValue)
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, filter.magValue)
            check(glGetTexLevelParameteri(GL_TEXTURE_2D_ARRAY, 0, GL_TEXTURE_DEPTH) == capacity) { "OpenGL did not allocate the requested texture-array depth of $capacity layers" }

            return textureId
        }
        catch (e: Exception)
        {
            glDeleteTextures(textureId)
            throw e
        }
        finally
        {
            glBindTexture(GL_TEXTURE_2D_ARRAY, previousTexture)
        }
    }

    private fun copyStorage(sourceId: Int, destinationId: Int)
    {
        if (sourceId == -1 || size == 0)
            return

        val previousReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val framebufferSrgbEnabled = glIsEnabled(GL_FRAMEBUFFER_SRGB)
        val scissorTestEnabled = glIsEnabled(GL_SCISSOR_TEST)
        val readFramebuffer = glGenFramebuffers()
        val drawFramebuffer = glGenFramebuffers()
        try
        {
            glDisable(GL_FRAMEBUFFER_SRGB)
            glDisable(GL_SCISSOR_TEST)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer)
            glDrawBuffer(GL_COLOR_ATTACHMENT0)

            for (level in 0 until mipLevels)
            {
                val levelSize = max(textureSize shr level, 1)
                for (layer in 0 until size)
                {
                    if (slotOwners[layer] == null)
                        continue

                    glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer)
                    glFramebufferTextureLayer(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, sourceId, level, layer)
                    check(glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Source texture layer $layer mip $level is not framebuffer-copyable" }

                    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer)
                    glFramebufferTextureLayer(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, destinationId, level, layer)
                    check(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Destination texture layer $layer mip $level is not framebuffer-copyable" }

                    glBlitFramebuffer(
                        0, 0, levelSize, levelSize,
                        0, 0, levelSize, levelSize,
                        GL_COLOR_BUFFER_BIT,
                        GL_NEAREST
                    )
                }
            }
        }
        finally
        {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
            if (framebufferSrgbEnabled) glEnable(GL_FRAMEBUFFER_SRGB) else glDisable(GL_FRAMEBUFFER_SRGB)
            if (scissorTestEnabled) glEnable(GL_SCISSOR_TEST) else glDisable(GL_SCISSOR_TEST)
            glDeleteFramebuffers(readFramebuffer)
            glDeleteFramebuffers(drawFramebuffer)
        }
    }

    private fun clearBaseLevel(textureId: Int)
    {
        val previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val clearFramebuffer = glGenFramebuffers()
        try
        {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, clearFramebuffer)
            glFramebufferTexture(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, textureId, 0)
            check(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Failed to create temporary framebuffer for clearing texture array #$textureId" }
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

    override fun toString(): String = "slot=$textureArraySlot, maxSize=${textureSize}px, layers=($size/$allocatedCapacity), format=$format, filter=$filter, anisotropy=$anisotropy, wrapping=$wrapping, mips=$mipLevels"

    companion object
    {
        private const val MAX_HANDLE_LAYER_COUNT = 32_768
        private const val MAX_TEXTURE_ARRAY_SLOT = 32_767

        private val ZERO_FLOAT_COLOR = floatArrayOf(0f, 0f, 0f, 0f)
        private val ZERO_INT_COLOR   = intArrayOf(0, 0, 0, 0)
    }
}

class TextureArrayAllocationException(message: String, cause: Throwable) : RuntimeException(message, cause)