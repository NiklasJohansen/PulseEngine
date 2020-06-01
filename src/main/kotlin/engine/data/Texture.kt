package engine.data

import de.matthiasmann.twl.utils.PNGDecoder
import engine.modules.Asset
import org.lwjgl.opengl.GL11.*
import java.nio.ByteBuffer


open class Texture(filename: String, override val name: String) : Asset(name, filename)
{
    var width: Int = 0
        private set

    var height: Int = 0
        private set

    var id: Int = -2
        private set
        get() {
            if(field == -2)
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

    open fun finalize(textureId: Int, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f)
    {
        this.textureData = null
        this.id = textureId
        this.uMin = uMin
        this.vMin = vMin
        this.uMax = uMax
        this.vMax = vMax
    }

    override fun load()
    {
        if (fileName.isNotBlank())
        {
            val decoder = PNGDecoder(Texture::class.java.getResourceAsStream(fileName))
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

    override fun delete()
    {
        glBindTexture(GL_TEXTURE_2D, 0)
        glDeleteTextures(id)
    }

    companion object
    {
        val BLANK = Texture("", "BLANK")
            .also {
                it.load(null, 1, 1, GL_RGBA)
                it.finalize(-1, 0f, 0f, 1f, 1f)
            }
    }
}