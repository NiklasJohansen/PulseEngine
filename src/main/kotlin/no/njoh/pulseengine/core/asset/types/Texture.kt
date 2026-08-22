package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAnisotropy.Companion.defaultFor
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.LINEAR_MIPMAP
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureHandle.Companion.INVALID
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.REPEAT
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAnisotropy
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureHandle
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytesFromPath
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memFree
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.FloatBuffer

@Icon("IMAGE")
open class Texture(
    filePath: String,
    name: String,
    initWidth: Int = 1,
    initHeight: Int = 1,
    val filter: TextureFilter = LINEAR_MIPMAP,
    val anisotropy: TextureAnisotropy = defaultFor(filter),
    val wrapping: TextureWrapping = REPEAT,
    val format: TextureFormat = SRGBA8,
    val maxMipLevels: Int = 15
) : Asset(filePath, name) {

    open val uploadExactTextureDimensions = false

    var handle = INVALID;    private set
    var width  = initWidth;  private set
    var height = initHeight; private set

    var uMin = 0f; private set
    var vMin = 0f; private set
    var uMax = 1f; private set
    var vMax = 1f; private set
    
    var pixelsLDR: ByteBuffer?  = null; private set
    var pixelsHDR: FloatBuffer? = null; private set

    var loadFailed = false; private set

    private var afterUpload: (Texture) -> Unit = { }

    override fun load()
    {
        if (filePath.isBlank() || pixelsLDR != null || pixelsHDR != null) return

        loadFailed = false
        try {
            val bytes = filePath.loadBytesFromPath() ?: throw FileNotFoundException("File not found: $filePath")
            val encodedPixels = memAlloc(bytes.size)
            try
            {
                encodedPixels.put(bytes).flip()
                val width = IntArray(1)
                val height = IntArray(1)
                val components = IntArray(1)

                stbi_info_from_memory(encodedPixels, width, height, components)

                when (format)
                {
                    SRGBA8, RGBA8 ->
                    {
                        if (stbi_is_hdr_from_memory(encodedPixels))
                            Logger.warn { "Loading HDR texture: $filePath into LDR format: $format" }

                        this.pixelsLDR = stbi_load_from_memory(encodedPixels, width, height, components, STBI_rgb_alpha)
                            ?: throw RuntimeException("Could not load image into memory: " + stbi_failure_reason())
                    }
                    RGBA16F, RGBA32F ->
                    {
                        this.pixelsHDR = stbi_loadf_from_memory(encodedPixels, width, height, components, STBI_rgb_alpha)
                            ?: throw RuntimeException("Could not load HDR image into memory: " + stbi_failure_reason())
                    }
                    else -> throw RuntimeException("Unsupported texture format: $format")
                }
                this.width = width[0]
                this.height = height[0]
                this.afterUpload = { tex ->
                    tex.pixelsLDR?.let { stbi_image_free(it) }
                    tex.pixelsHDR?.let { stbi_image_free(it) }
                }
            }
            finally
            {
                memFree(encodedPixels)
            }
        }
        catch (e: Exception)
        {
            loadFailed = true
            Logger.error { "Failed to load image $filePath: ${e.message}" }
        }
    }

    fun loadFrom(pixels: ByteBuffer?, width: Int, height: Int, freeWithStbi: Boolean)
    {
        this.loadFailed = (pixels == null)
        this.pixelsLDR = pixels
        this.width = width
        this.height = height
        this.afterUpload = { tex -> if (freeWithStbi) tex.pixelsLDR?.let { stbi_image_free(it) } }
        if (pixels == null)
            Logger.error { "Failed to load image data for texture '$name': pixel buffer is null" }
    }

    open fun onUploaded(handle: TextureHandle, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f)
    {
        require(handle.isArrayTexture || handle.isNone) { "Textures must use an array texture handle or NONE" }
        this.handle = handle
        this.uMin = uMin
        this.vMin = vMin
        this.uMax = uMax
        this.vMax = vMax
        this.afterUpload(this)
        this.pixelsLDR = null
        this.pixelsHDR = null
    }

    open fun onDeleted()
    {
        this.handle = INVALID
    }

    override fun unload() { }

    companion object
    {
        val BLANK = Texture(filePath = "", name = "BLANK").also { it.onUploaded(TextureHandle.NONE) }
    }
}