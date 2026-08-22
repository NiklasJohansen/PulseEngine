package no.njoh.pulseengine.core.graphics.gpu.resource

import gnu.trove.map.hash.THashMap
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAnisotropy.OFF
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureArray
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureArrayAllocationException
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureHandle
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.CLAMP_TO_EDGE
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glPixelStorei
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL12.GL_TEXTURE_BASE_LEVEL
import org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL
import org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24
import kotlin.math.max
import kotlin.math.min

class TextureBank
{
    private val textureArrays = mutableListOf<TextureArray>()
    private val emptyTextureArray = TextureArray(0, 0, 0, RGBA8, LINEAR, OFF, CLAMP_TO_EDGE, 1)
    private val fallbackTextures = THashMap<Color, RenderTexture>()
    private var fallbackDepthTexture: RenderTexture? = null

    fun upload(texture: Texture)
    {
        if (texture.loadFailed)
        {
            texture.onUploaded(handle = TextureHandle.NONE)
            return
        }

        val array = getOrCreateTextureArrayFor(texture)
        if (array != null)
        {
            try
            {
                array.upload(texture)
                return
            }
            catch (e: TextureArrayAllocationException)
            {
                if (array.id == -1 && array.size == 0)
                    textureArrays.remove(array)

                Logger.error(e) { "Failed to upload texture: ${texture.filePath}" }
            }
        }

        // Fall back to no texture if the upload failed
        texture.onUploaded(handle = TextureHandle.NONE)
    }

    fun delete(texture: Texture)
    {
        val handle = texture.handle
        if (handle.isArrayTexture)
            textureArrays.firstOrNullFast { it.textureArraySlot == handle.textureArraySlot }?.delete(texture)
        texture.onDeleted()
    }

    fun destroy()
    {
        textureArrays.forEachFast { it.destroy() }
        emptyTextureArray.destroy()
        fallbackTextures.forEachValue { glDeleteTextures(it.handle.glId); true }
        fallbackTextures.clear()
        fallbackDepthTexture?.let { glDeleteTextures(it.handle.glId) }
        fallbackDepthTexture = null
    }

    fun generatePendingMipmaps()
    {
        textureArrays.forEachFast { it.generatePendingMipmaps() }
    }

    fun getTextureArray(texture: Texture?): TextureArray?
    {
        if (texture == null || !texture.handle.isArrayTexture)
            return null

        return textureArrays.firstOrNullFast { it.textureArraySlot == texture.handle.textureArraySlot }
    }

    fun getTextureArrayOrDefault(texture: Texture?): TextureArray =
        getTextureArray(texture) ?: emptyTextureArray.also { if (it.id == -1) it.init() }

    fun getAllTextureArrays(): List<TextureArray> = textureArrays

    fun getOrCreateFallbackTexture(color: Color): RenderTexture =
        fallbackTextures.getOrPut(color) { createFallbackTexture(color) }

    fun getOrCreateFallbackDepthTexture(): RenderTexture =
        fallbackDepthTexture ?: createFallbackDepthTexture().also { fallbackDepthTexture = it }

    private fun createFallbackTexture(color: Color): RenderTexture
    {
        val pixels = BufferUtils.createByteBuffer(4)
        pixels.put((color.red * 255).toInt().coerceIn(0, 255).toByte())
        pixels.put((color.green * 255).toInt().coerceIn(0, 255).toByte())
        pixels.put((color.blue * 255).toInt().coerceIn(0, 255).toByte())
        pixels.put((color.alpha * 255).toInt().coerceIn(0, 255).toByte())
        pixels.flip()

        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        val previousUnpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT)
        try
        {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0)
        }
        finally
        {
            glPixelStorei(GL_UNPACK_ALIGNMENT, previousUnpackAlignment)
            glBindTexture(GL_TEXTURE_2D, 0)
        }

        return RenderTexture(name = "fallback", handle = TextureHandle.createGlHandle(id), width = 1, height = 1)
    }

    private fun createFallbackDepthTexture(): RenderTexture
    {
        val pixel = BufferUtils.createFloatBuffer(1)
        pixel.put(1f)
        pixel.flip()

        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, 1, 1, 0, GL_DEPTH_COMPONENT, GL_FLOAT, pixel)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0)
        glBindTexture(GL_TEXTURE_2D, 0)

        return RenderTexture(name = "fallback_depth", handle = TextureHandle.createGlHandle(id), width = 1, height = 1)
    }

    private fun getOrCreateTextureArrayFor(texture: Texture): TextureArray?
    {
        val imageSize = max(texture.width, texture.height)
        val textureWidth: Int
        val textureHeight: Int
        try
        {
            validateTextureDimensions(texture.width, texture.height, GlCapabilities.limits.maxTextureSize)
            if (texture.uploadExactTextureDimensions)
            {
                textureWidth = texture.width
                textureHeight = texture.height
            }
            else
            {
                val textureSize = calculateTextureArraySize(imageSize, GlCapabilities.limits.maxTextureSize)
                textureWidth = textureSize
                textureHeight = textureSize
            }
        }
        catch (e: IllegalArgumentException)
        {
            Logger.error { "Failed to load texture '${texture.filePath}': ${e.message}" }
            return null
        }

        val textureArray = textureArrays.firstOrNullFast()
        {
            it.textureWidth == textureWidth &&
            it.textureHeight == textureHeight &&
            it.format == texture.format &&
            it.filter == texture.filter &&
            it.anisotropy == texture.anisotropy &&
            it.wrapping == texture.wrapping &&
            it.maxMipLevels == texture.maxMipLevels &&
            !it.isFull()
        }

        if (textureArray != null)
            return textureArray

        if (textureArrays.size >= MAX_TEXTURE_SLOTS)
        {
            Logger.error()
            {
                "Failed to load texture: name=${texture.name}, size=${texture.width}x${texture.height}px, format=${texture.format}, " +
                "filter=${texture.filter}, anisotropy=${texture.anisotropy}, wrapping=${texture.wrapping} and maxMipLevels=${texture.maxMipLevels}.\n" +
                "All $MAX_TEXTURE_SLOTS texture array slots are in use:\n\n" +
                textureArrays.joinToString("\n") { "  $it" } +
                "\n\nConsider reducing the number of texture sampler permutations."
            }
            return null
        }

        val newArray = TextureArray(textureArrays.size, textureWidth, textureHeight, texture.format, texture.filter, texture.anisotropy, texture.wrapping, texture.maxMipLevels)
        textureArrays.add(newArray)
        Logger.debug { "New texture array created: $newArray" }

        return newArray
    }

    private fun validateTextureDimensions(width: Int, height: Int, maximumTextureSize: Int)
    {
        require(width > 0 && height > 0) { "Texture dimensions must be positive: ${width}x${height}" }
        require(width <= maximumTextureSize && height <= maximumTextureSize) { "Texture dimensions are ${width}x${height}px, but this device supports at most ${maximumTextureSize}x${maximumTextureSize}px" }
    }

    private fun calculateTextureArraySize(imageSize: Int, maximumTextureSize: Int): Int
    {
        val minimumSize = max(imageSize, MIN_TEXTURE_SIZE)
        val powerOfTwoSize = Integer.highestOneBit(minimumSize - 1) shl 1
        return min(powerOfTwoSize, maximumTextureSize)
    }

    companion object
    {
        private const val MIN_TEXTURE_SIZE = 128
        private const val MAX_TEXTURE_SLOTS = 16
    }
}