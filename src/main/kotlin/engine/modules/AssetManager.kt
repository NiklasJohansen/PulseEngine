package engine.modules

import engine.data.*

// Exposed to game code
interface AssetManagerInterface
{
    fun <T: Asset> get(assetName: String): T
    fun <T: Asset> getAll(type: Class<T>): List<T>

    fun loadImage(filename: String, assetName: String): Image
    fun loadFont(filename: String, assetName: String, fontSizes: FloatArray): Font
    fun loadSound(filename: String, assetName: String): Sound
    fun loadText(filename: String, assetName: String): Text
    fun loadBinary(filename: String, assetName: String): Binary
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

    override fun <T : Asset> get(assetName: String): T
    {
        val asset = assets[assetName] ?: throw IllegalArgumentException("No asset loaded with name: $assetName")
        return asset as T
    }

    override fun <T : Asset> getAll(type: Class<T>): List<T>
        = assets.values.filterIsInstance(type)

    override fun loadImage(filename: String, assetName: String): Image
        = Image.create(filename, assetName).also { assets[assetName] = it  }

    override fun loadFont(filename: String, assetName: String, fontSizes: FloatArray): Font
        = Font.create(filename, assetName, fontSizes).also { assets[assetName] = it }

    override fun loadSound(filename: String, assetName: String): Sound
        = Sound.create(filename, assetName).also { assets[assetName] = it  }

    override fun loadText(filename: String, assetName: String): Text
        = Text.create(filename, assetName).also { assets[assetName] = it }

    override fun loadBinary(filename: String, assetName: String): Binary
        = Binary.create(filename, assetName).also { assets[assetName] = it }

    override fun cleanUp()
    {
        println("Cleaning up assets...")
        assets.values.filterIsInstance<Image>().forEach { Image.delete(it) }
        assets.values.filterIsInstance<Sound>().forEach { Sound.delete(it) }
        assets.values.filterIsInstance<Font>().forEach  { Font.delete(it) }
    }
}

abstract class Asset(open val name: String)


