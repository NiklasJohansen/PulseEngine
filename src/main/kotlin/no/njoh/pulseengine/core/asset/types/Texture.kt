package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA8
import no.njoh.pulseengine.core.graphics.api.TextureHandle
import no.njoh.pulseengine.core.graphics.api.TextureHandle.Companion.INVALID
import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytes
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImage.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer

@ScnIcon("IMAGE")
open class Texture(
    filename: String,
    override val name: String,
    val filter: TextureFilter = LINEAR,
    val mipLevels: Int = 5
) : Asset(name, filename) {

    var handle: TextureHandle = INVALID
        private set

    var width: Int = 1
        private set

    var height: Int = 1
        private set

    var uMin: Float = 0f
        private set

    var vMin: Float = 0f
        private set

    var uMax: Float = 1f
        private set

    var vMax: Float = 1f
        private set

    var isBindless: Boolean = false
        private set

    var format: TextureFormat = RGBA8
        private set

    var pixelsLDR: ByteBuffer? = null
        private set

    var pixelsHDR: FloatBuffer? = null
        private set

    var attachment: Int = -1
        private set

    private var onFinalized: (Texture) -> Unit = { }

    open fun finalize(handle: TextureHandle, isBindless: Boolean, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f, attachment: Int = -1)
    {
        this.handle = handle
        this.isBindless = isBindless
        this.uMin = uMin
        this.vMin = vMin
        this.uMax = uMax
        this.vMax = vMax
        this.attachment = attachment
        this.onFinalized(this)
        this.pixelsLDR = null
        this.pixelsHDR = null
    }

    override fun load()
    {
        if (fileName.isBlank()) return

        try {
            val bytes = fileName.loadBytes() ?: throw RuntimeException("No such file")
            val buffer = BufferUtils.createByteBuffer(bytes.size).put(bytes).flip()
            val width = IntArray(1)
            val height = IntArray(1)
            val components = IntArray(1)

            stbi_info_from_memory(buffer, width, height, components)

            if (stbi_is_hdr_from_memory(buffer))
            {
                this.format = RGBA16F
                this.pixelsHDR = stbi_loadf_from_memory(buffer, width, height, components, STBI_rgb_alpha)
                    ?: throw RuntimeException("Could not load HDR image into memory: " + stbi_failure_reason())
            }
            else
            {
                this.format = RGBA8
                this.pixelsLDR = stbi_load_from_memory(buffer, width, height, components, STBI_rgb_alpha)
                    ?: throw RuntimeException("Could not load image into memory: " + stbi_failure_reason())
            }
            this.width = width[0]
            this.height = height[0]
            this.onFinalized = { tex ->
                tex.pixelsLDR?.let { stbi_image_free(it) }
                tex.pixelsHDR?.let { stbi_image_free(it) }
            }
        }
        catch (e: Exception)
        {
            Logger.error("Failed to load image $fileName: ${e.message}")
        }
    }

    fun stage(pixels: ByteBuffer?, width: Int, height: Int)
    {
        this.pixelsLDR = pixels
        this.format = RGBA8
        this.width = width
        this.height = height
    }

    override fun delete() { }

    companion object
    {
        val SUPPORTED_FORMATS = listOf("png", "jpg", "jpeg", "hdr")
        val BLANK = Texture("", "BLANK").apply {
            finalize(handle = TextureHandle.NONE, isBindless = true, uMin = 0f, vMin = 0f, uMax = 1f, vMax = 1f)
        }
        val BLANK_BINDABLE = Texture("", "BLANK_BINDABLE").apply {
            finalize(handle = TextureHandle.NONE, isBindless = false, uMin = 0f, vMin = 0f, uMax = 1f, vMax = 1f)
        }
    }
}