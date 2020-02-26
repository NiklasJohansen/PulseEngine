package engine.modules

import engine.data.Font
import engine.data.Image
import org.lwjgl.opengl.GL11.*

// Exposed to game code
interface AssetManagerInterface
{
    fun <T: Asset> get(assetName: String): T?

    fun loadImage(filename: String, assetName: String)
    fun loadFont(filename: String, assetName: String, fontSizes: FloatArray)
    fun loadSound(filename: String, assetName: String)
    fun loadText(filename: String, assetName: String)
    fun loadBinary(filename: String, assetName: String)
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

    override fun loadImage(filename: String, assetName: String)
    {
        assets[assetName] = Image.create(filename, assetName)
    }

    override fun loadFont(filename: String, assetName: String, fontSizes: FloatArray)
    {
        assets[assetName] = Font.create(filename, assetName, fontSizes)
    }

    override fun loadSound(filename: String, assetName: String)
    {
        assets[assetName] = Sound(assetName, "...")
    }

    override fun loadText(filename: String, assetName: String)
    {
        assets[assetName] = Text(assetName, javaClass.getResource(filename).readText())
    }
    override fun loadBinary(filename: String, assetName: String)
    {
        assets[assetName] = Binary(assetName, javaClass.getResource(filename).readBytes())
    }

    override fun cleanUp()
    {
        println("Cleaning up assets...")
        assets.values.filterIsInstance<Image>().forEach { glDeleteTextures(it.textureId) }
        assets.values.filterIsInstance<Font>().forEach {
            glDeleteTextures(it.characterImage.textureId)
            it.characterData.free()
        }
    }
}


abstract class Asset(open val name: String)

data class Sound(override val name: String, val someSoundData: String) : Asset(name)

data class Text(override val name: String, val text: String) : Asset(name)

data class Binary(override val name: String, val bytes: ByteArray) : Asset(name)


