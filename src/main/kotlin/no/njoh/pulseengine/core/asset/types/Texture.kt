package no.njoh.pulseengine.core.asset.types

import de.matthiasmann.twl.utils.PNGDecoder
import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.loadStream
import org.lwjgl.opengl.GL11.*
import java.nio.ByteBuffer

@ScnIcon("IMAGE")
open class Texture(filename: String, override val name: String) : Asset(name, filename)
{
    var width: Int = 0
        private set

    var height: Int = 0
        private set

    var id: Int = -2
        private set
        get() {
            if (field == -2)
                throw IllegalStateException("Accessing unfinalized texture with asset name: $name")
            return field
        }

    internal var uMin: Float = 0f
        private set

    internal var vMin: Float = 0f
        private set

    internal var uMax: Float = 1f
        private set

    internal var vMax: Float = 1f
        private set

    internal var textureData: ByteBuffer? = null
        private set

    internal var format: Int = 0
        private set

    internal var isBindless = false
        private set

    internal var attachment: Int = -1
        private set

    open fun finalize(textureId: Int, isBindless: Boolean, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f, attachment: Int = -1)
    {
        this.id = textureId
        this.isBindless = isBindless
        this.uMin = uMin
        this.vMin = vMin
        this.uMax = uMax
        this.vMax = vMax
        this.attachment = attachment
        this.textureData = null
    }

    override fun load()
    {
        if (fileName.isNotBlank())
        {
            val stream = fileName.loadStream() ?: run {
                Logger.error("Failed to find and load Texture asset: $fileName")
                load(ByteBuffer.allocate(0), 0, 0, GL_RGBA)
                return
            }

            val decoder = PNGDecoder(stream)
            val buffer = ByteBuffer.allocateDirect(4 * decoder.width * decoder.height)
            decoder.decode(buffer, decoder.width * 4, PNGDecoder.Format.RGBA)
            buffer.flip()
            load(buffer, decoder.width, decoder.height, GL_RGBA)
        }
    }

    fun load(textureData: ByteBuffer?, width: Int, height: Int, format: Int)
    {
        this.textureData = textureData
        this.width = width
        this.height = height
        this.format = format
    }

    override fun delete() { }

    companion object
    {
        val SUPPORTED_FORMATS = listOf("png")
        val BLANK = Texture("", "BLANK").apply {
            load(textureData = null, width = 1, height = 1, format = GL_RGBA)
            finalize(textureId = -1, isBindless = true, uMin = 0f, vMin = 0f, uMax = 1f, vMax = 1f)
        }
        val BLANK_BINDABLE = Texture("", "BLANK_BINDABLE").apply {
            load(textureData = null, width = 1, height = 1, format = GL_RGBA)
            finalize(textureId = -1, isBindless = false, uMin = 0f, vMin = 0f, uMax = 1f, vMax = 1f)
        }
    }
}