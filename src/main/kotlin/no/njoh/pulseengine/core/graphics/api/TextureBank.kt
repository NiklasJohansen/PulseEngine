package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.*
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import no.njoh.pulseengine.core.shared.utils.Logger
import kotlin.math.max

class TextureBank
{
    private val capacitySpecs = mutableListOf<TextureCapacitySpec>().apply { addAll(DEFAULT_CAPACITIES) }
    private val textureArrays = mutableListOf<TextureArray>()
    private val emptyTextureArray = TextureArray(0, 0, 0, RGBA8, LINEAR, CLAMP_TO_EDGE, 1)

    fun upload(texture: Texture)
    {
        val array = getOrCreateTextureArrayFor(texture)
        if (array != null)
        {
            if (!array.isFull())
            {
                array.upload(texture)
                return
            }
            else Logger.error(
                "Failed to load texture: ${texture.fileName}. Texture array for " +
                "textureSize=${array.textureSize}px and format=${array.format} is full " +
                "(${array.size}/${array.maxCapacity}). Consider increasing its capacity."
            )
        }

        // Fall back to no texture if the upload failed
        texture.finalize(handle = TextureHandle.NONE, isBindless = true)
    }

    fun delete(texture: Texture)
    {
        val samplerIndex = texture.handle.samplerIndex
        textureArrays.find { it.samplerIndex == samplerIndex }?.delete(texture)
    }

    fun destroy()
    {
        textureArrays.forEachFast { it.destroy() }
    }

    fun setTextureCapacity(maxCount: Int, textureSize: Int, format: TextureFormat = RGBA8)
    {
        capacitySpecs.removeWhen { it.texSize == textureSize && it.format == format }
        capacitySpecs.add(TextureCapacitySpec(textureSize, maxCount, format))
        capacitySpecs.sortBy { it.texSize }
    }

    fun getTextureArray(texture: Texture?): TextureArray? =
        textureArrays.firstOrNullFast { it.samplerIndex == texture?.handle?.samplerIndex }

    fun getTextureArrayOrDefault(texture: Texture?): TextureArray =
        getTextureArray(texture) ?: emptyTextureArray.also { if (it.id == -1) it.init() }

    fun getAllTextureArrays(): List<TextureArray> = textureArrays

    private fun getOrCreateTextureArrayFor(texture: Texture): TextureArray?
    {
        val textureSize = max(texture.width, texture.height)
        val textureArray = textureArrays.find()
        {
            it.textureSize >= textureSize &&
            it.format == texture.format &&
            it.filter == texture.filter &&
            it.wrapping == texture.wrapping &&
            it.mipLevels == texture.mipLevels
        }

        val isMoreTextureSlotsAvailable = textureArrays.size < MAX_TEXTURE_SLOTS
        if (textureArray != null)
        {
            val foundArrayHasAppropriateSize = textureSize > textureArray.textureSize / 2
            val isTextureSmallerThanSmallestSpec = textureSize < capacitySpecs.first().texSize
            if (foundArrayHasAppropriateSize || isTextureSmallerThanSmallestSpec || !isMoreTextureSlotsAvailable)
                return textureArray
        }

        if (!isMoreTextureSlotsAvailable)
        {
            Logger.error(
                "Failed to load texture: name=${texture.name}, size=${textureSize}px, format=${texture.format}, " +
                "filter=${texture.filter}, wrapping=${texture.wrapping} and mipLevels=${texture.mipLevels}.\n" +
                "All $MAX_TEXTURE_SLOTS texture array slots are in use:\n\n" +
                textureArrays.joinToString("\n") { "  $it" } +
                "\n\nConsider reducing the number of texture sampler permutations."
            )
            return null
        }

        val closestTextureSize = capacitySpecs.firstOrNull { it.texSize >= textureSize }?.texSize
        val spec = capacitySpecs.find { it.texSize == closestTextureSize && it.format == texture.format }
        if (spec == null)
        {
            Logger.error("Failed to load texture: ${texture.fileName}. No texture capacity set for textures with size=${textureSize}px and format ${texture.format}.")
            return null
        }

        val newArray = TextureArray(textureArrays.size, spec.texSize, spec.capacity, texture.format, texture.filter, texture.wrapping, texture.mipLevels)
        textureArrays.add(newArray)
        textureArrays.sortBy { it.textureSize }
        Logger.debug("New texture array created: $newArray")

        return newArray
    }

    private data class TextureCapacitySpec(
        val texSize: Int,
        val capacity: Int,
        val format: TextureFormat
    )

    companion object
    {
        private const val MAX_TEXTURE_SLOTS = 16
        private val DEFAULT_CAPACITIES = listOf(
            TextureCapacitySpec(texSize = 128,  capacity = 100, format = SRGBA8),
            TextureCapacitySpec(texSize = 128,  capacity = 100, format = RGBA8),
            TextureCapacitySpec(texSize = 128,  capacity = 70,  format = RGBA16F),
            TextureCapacitySpec(texSize = 128,  capacity = 50,  format = RGBA32F),

            TextureCapacitySpec(texSize = 256,  capacity = 100, format = SRGBA8),
            TextureCapacitySpec(texSize = 256,  capacity = 100, format = RGBA8),
            TextureCapacitySpec(texSize = 256,  capacity = 70,  format = RGBA16F),
            TextureCapacitySpec(texSize = 256,  capacity = 50,  format = RGBA32F),

            TextureCapacitySpec(texSize = 512,  capacity = 50,  format = SRGBA8),
            TextureCapacitySpec(texSize = 512,  capacity = 50,  format = RGBA8),
            TextureCapacitySpec(texSize = 512,  capacity = 30,  format = RGBA16F),
            TextureCapacitySpec(texSize = 512,  capacity = 20,  format = RGBA32F),

            TextureCapacitySpec(texSize = 1024, capacity = 50,  format = SRGBA8),
            TextureCapacitySpec(texSize = 1024, capacity = 50,  format = RGBA8),
            TextureCapacitySpec(texSize = 1024, capacity = 30,  format = RGBA16F),
            TextureCapacitySpec(texSize = 1024, capacity = 20,  format = RGBA32F),

            TextureCapacitySpec(texSize = 2048, capacity = 15,  format = SRGBA8),
            TextureCapacitySpec(texSize = 2048, capacity = 15,  format = RGBA8),
            TextureCapacitySpec(texSize = 2048, capacity = 10,  format = RGBA16F),
            TextureCapacitySpec(texSize = 2048, capacity = 5,   format = RGBA32F),

            TextureCapacitySpec(texSize = 4096, capacity = 10,  format = SRGBA8),
            TextureCapacitySpec(texSize = 4096, capacity = 10,  format = RGBA8),
            TextureCapacitySpec(texSize = 4096, capacity = 5,   format = RGBA16F),
            TextureCapacitySpec(texSize = 4096, capacity = 5,   format = RGBA32F),

            TextureCapacitySpec(texSize = 8192, capacity = 5,   format = SRGBA8),
            TextureCapacitySpec(texSize = 8192, capacity = 5,   format = RGBA8),
            TextureCapacitySpec(texSize = 8192, capacity = 3,   format = RGBA16F),
            TextureCapacitySpec(texSize = 8192, capacity = 2,   format = RGBA32F)
        )
    }
}