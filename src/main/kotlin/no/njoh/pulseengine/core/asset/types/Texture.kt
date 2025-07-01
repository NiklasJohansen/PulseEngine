package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR_MIPMAP
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.TextureHandle.Companion.INVALID
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.REPEAT
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytesFromDisk
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImage.*
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
    val wrapping: TextureWrapping = REPEAT,
    val format: TextureFormat = SRGBA8,
    val maxMipLevels: Int = 5,
) : Asset(filePath, name) {

    var handle = INVALID;    private set
    var width  = initWidth;  private set
    var height = initHeight; private set

    var uMin = 0f; private set
    var vMin = 0f; private set
    var uMax = 1f; private set
    var vMax = 1f; private set

    var pixelsLDR: ByteBuffer?  = null; private set
    var pixelsHDR: FloatBuffer? = null; private set

    private var afterUpload: (Texture) -> Unit = { }

    override fun load()
    {
        if (filePath.isBlank()) return

        try {
            val bytes = filePath.loadBytesFromDisk() ?: throw FileNotFoundException("File not found: $filePath")
            val buffer = BufferUtils.createByteBuffer(bytes.size).put(bytes).flip() as ByteBuffer
            val width = IntArray(1)
            val height = IntArray(1)
            val components = IntArray(1)

            stbi_info_from_memory(buffer, width, height, components)

            when (format)
            {
                SRGBA8, RGBA8 ->
                {
                    if (stbi_is_hdr_from_memory(buffer))
                        Logger.warn { "Loading HDR texture: $filePath into LDR format: $format" }

                    this.pixelsLDR = stbi_load_from_memory(buffer, width, height, components, STBI_rgb_alpha)
                        ?: throw RuntimeException("Could not load image into memory: " + stbi_failure_reason())
                }
                RGBA16F, RGBA32F ->
                {
                    this.pixelsHDR = stbi_loadf_from_memory(buffer, width, height, components, STBI_rgb_alpha)
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
        catch (e: Exception)
        {
            Logger.error { "Failed to load image $filePath: ${e.message}" }
        }
    }

    fun loadFrom(pixels: ByteBuffer?, width: Int, height: Int)
    {
        this.pixelsLDR = pixels
        this.width = width
        this.height = height
    }

    open fun onUploaded(handle: TextureHandle, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f)
    {
        this.handle = handle
        this.uMin = uMin
        this.vMin = vMin
        this.uMax = uMax
        this.vMax = vMax
        this.afterUpload(this)
        this.pixelsLDR = null
        this.pixelsHDR = null
    }

    override fun unload() { }

    companion object
    {
        val BLANK = Texture(filePath = "", name = "BLANK").also { it.onUploaded(TextureHandle.NONE) }
    }
}