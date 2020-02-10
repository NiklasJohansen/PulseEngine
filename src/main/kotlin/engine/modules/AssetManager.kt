package engine.modules

import de.matthiasmann.twl.utils.PNGDecoder
import org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap
import org.lwjgl.opengl.GL11.*
import java.nio.ByteBuffer

// Exposed to game code
interface AssetManagerInterface
{
    fun <T: Asset> get(assetName: String): T?
    fun <T: Asset> load(filename: String, assetName: String, type: Class<T>): T
}

// Exposed to game engine
interface AssetManagerEngineInterface : AssetManagerInterface
{
    fun init()
    fun cleanUp()
}

class AssetManager : AssetManagerEngineInterface
{
    private val assets = mutableMapOf<String, Asset>()

    override fun init()
    {
        println("Initializing asset manager...")
    }

    override fun <T : Asset> get(assetName: String): T?
    {
        return assets[assetName]?.let { it as T }
    }

    override fun <T : Asset> load(filename: String, assetName: String, type: Class<T>): T
    {
        val asset = when
        {
            type.isAssignableFrom(Image::class.java)  -> loadImage(filename, assetName)
            type.isAssignableFrom(Sound::class.java)  -> loadSound(filename, assetName)
            type.isAssignableFrom(Text::class.java)   -> loadText(filename, assetName)
            type.isAssignableFrom(Binary::class.java) -> loadBinary(filename, assetName)
            else -> throw IllegalArgumentException("Asset type (${type.name}) not supported")
        }
        assets[assetName] = asset
        return asset as T
    }

    private fun loadSound(filename: String, assetName: String): Sound
            = Sound(assetName, "...")

    private fun loadText(filename: String, assetName: String): Text
            = Text(assetName, javaClass.getResource(filename).readText())

    private fun loadBinary(filename: String, assetName: String): Binary
            = Binary(assetName, javaClass.getResource(filename).readBytes())

    private fun loadImage(filename: String, assetName: String): Image
    {
        val decoder = PNGDecoder(javaClass.getResourceAsStream(filename))
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(4 * decoder.width * decoder.height)
        decoder.decode(buffer, decoder.width * 4, PNGDecoder.Format.RGBA)
        buffer.flip()

        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, decoder.width, decoder.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        glGenerateMipmap(GL_TEXTURE_2D)

        return Image(assetName, id, decoder.width, decoder.height)
    }

    override fun cleanUp()
    {
        println("Cleaning up assets...")
        assets.values.filterIsInstance<Image>().forEach { glDeleteTextures(it.textureId) }
    }
}

sealed class Asset(open val name: String)

data class Image(override val name: String, val textureId: Int, val width: Int, val height: Int) : Asset(name)

data class Sound(override val name: String, val someSoundData: String) : Asset(name)

data class Text(override val name: String, val text: String) : Asset(name)

data class Binary(override val name: String, val bytes: ByteArray) : Asset(name)


