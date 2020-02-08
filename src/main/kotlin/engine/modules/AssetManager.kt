package engine.modules

import java.lang.IllegalArgumentException

interface AssetManagerInterface
{
    fun <T : Asset> get(assetName: String): T?
    fun <T: Asset> load(filename: String, assetName: String, type: Class<T>): T
}

class AssetManager : AssetManagerInterface
{
    private val assets = mutableMapOf<String, Asset>()

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

    private fun loadImage(filename: String, assetName: String): Image
            = Image(assetName, 0)

    private fun loadSound(filename: String, assetName: String): Sound
            = Sound(assetName, "...")

    private fun loadText(filename: String, assetName: String): Text
            = Text(assetName, javaClass.getResource(filename).readText())

    private fun loadBinary(filename: String, assetName: String): Binary
            = Binary(assetName, javaClass.getResource(filename).readBytes())

    fun cleanUp()
    {
        println("Cleaning up assets")
    }
}

sealed class Asset(open val name: String)

data class Image(override val name: String, val textureId: Int) : Asset(name)

data class Sound(override val name: String, val someSoundData: String) : Asset(name)

data class Text(override val name: String, val text: String) : Asset(name)

data class Binary(override val name: String, val bytes: ByteArray) : Asset(name)


