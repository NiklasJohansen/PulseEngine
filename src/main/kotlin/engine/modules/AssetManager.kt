package engine.modules

import engine.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// Exposed to game code
interface AssetManagerInterface
{
    fun <T: Asset> add(asset: T): T
    fun <T: Asset> get(assetName: String): T
    fun <T: Asset> getAll(type: Class<T>): List<T>

    fun loadTexture(filename: String, assetName: String): Texture
    fun loadSpriteSheet(filename: String, assetName: String, horizontalCells: Int, verticalCells: Int): SpriteSheet
    fun loadFont(filename: String, assetName: String, fontSizes: FloatArray): Font
    fun loadSound(filename: String, assetName: String): Sound
    fun loadText(filename: String, assetName: String): Text
    fun loadBinary(filename: String, assetName: String): Binary
}

// Exposed to game engine
interface AssetManagerEngineInterface : AssetManagerInterface
{
    fun loadInitialAssets()
    fun setOnAssetLoaded(callback: (Asset) -> Unit)
    fun cleanUp()
}

class AssetManager : AssetManagerEngineInterface
{
    private val assets = mutableMapOf<String, Asset>()
    private var initialAssetsLoaded = false
    private var onAssetLoadedCallback: (Asset) -> Unit = {}

    override fun <T : Asset> get(assetName: String): T
    {
        val asset = assets[assetName] ?: throw IllegalArgumentException("No asset loaded with name: $assetName")
        return asset as T
    }

    override fun <T : Asset> getAll(type: Class<T>): List<T>
        = assets.values.filterIsInstance(type)

    override fun loadTexture(filename: String, assetName: String): Texture
        = Texture(filename, assetName).also { add(it)  }

    override fun loadSpriteSheet(filename: String, assetName: String, horizontalCells: Int, verticalCells: Int): SpriteSheet
        = SpriteSheet(filename, assetName, horizontalCells, verticalCells).also{ add(it) }

    override fun loadFont(filename: String, assetName: String, fontSizes: FloatArray): Font
        = Font(filename, assetName, fontSizes).also { add(it) }

    override fun loadSound(filename: String, assetName: String): Sound
        = Sound(filename, assetName).also { add(it)  }

    override fun loadText(filename: String, assetName: String): Text
        = Text(filename, assetName).also { add(it) }

    override fun loadBinary(filename: String, assetName: String): Binary
        = Binary(filename, assetName).also { add(it) }

    override fun loadInitialAssets()
    {
        println("Loading assets...")
        runBlocking {
            assets.values.forEach {
                launch {
                    it.load()
                    println("Loaded asset: ${it.name}")
                }
            }
        }
        assets.values.forEach(onAssetLoadedCallback)
        initialAssetsLoaded = true
    }

    override fun <T: Asset> add(asset: T): T
    {
        assets[asset.name] = asset
        if (initialAssetsLoaded) {
            asset.load()
            onAssetLoadedCallback.invoke(asset)
        }
        return asset
    }

    override fun setOnAssetLoaded(callback: (Asset) -> Unit)
    {
        this.onAssetLoadedCallback = callback
    }

    override fun cleanUp()
    {
        println("Cleaning up assets...")
        assets.values.forEach { it.delete() }
    }
}


abstract class Asset(open val name: String, protected val fileName: String)
{
    abstract fun load()
    abstract fun delete()
}


