package no.njoh.pulseengine.modules

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.njoh.pulseengine.data.assets.*
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.forEachFiltered
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.reflect.KClass

abstract class Assets
{
    abstract fun <T : Asset> add(asset: T): T
    abstract fun <T : Asset> get(assetName: String): T
    abstract fun <T : Asset> getAll(type: Class<T>): List<T>

    inline fun <reified T : Asset> getSafe(assetName: String): T? = getSafe(assetName, T::class)
    @PublishedApi internal abstract fun <T : Asset> getSafe(assetName: String, type: KClass<T>): T?

    abstract fun loadAllTextures(directory: String)
    abstract fun loadTexture(filename: String, assetName: String): Texture
    abstract fun loadSpriteSheet(filename: String, assetName: String, horizontalCells: Int, verticalCells: Int): SpriteSheet
    abstract fun loadFont(filename: String, assetName: String, fontSizes: FloatArray): Font
    abstract fun loadCursor(filename: String, assetName: String, xHotSpot: Int, yHotSpot: Int): Cursor
    abstract fun loadSound(filename: String, assetName: String): Sound
    abstract fun loadText(filename: String, assetName: String): Text
    abstract fun loadBinary(filename: String, assetName: String): Binary
}

abstract class AssetsEngineInterface : Assets()
{
    abstract fun loadInitialAssets()
    abstract fun setOnAssetLoaded(callback: (Asset) -> Unit)
    abstract fun cleanUp()
}

class AssetsImpl : AssetsEngineInterface()
{
    private val assets = mutableMapOf<String, Asset>()
    private var initialAssetsLoaded = false
    private var onAssetLoadedCallback: (Asset) -> Unit = {}

    @Suppress("UNCHECKED_CAST")
    override fun <T : Asset> get(assetName: String): T =
        assets[assetName]?.let { it as T } ?: throw IllegalArgumentException("No asset loaded with name: $assetName")

    @Suppress("UNCHECKED_CAST")
    override fun <T : Asset> getSafe(assetName: String, type: KClass<T>): T? =
        assets[assetName]?.takeIf { it::class == type }?.let { it as T }

    override fun <T : Asset> getAll(type: Class<T>): List<T>
        = assets.values.filterIsInstance(type)

    override fun loadTexture(filename: String, assetName: String): Texture
        = Texture(filename, assetName).also { add(it)  }

    override fun loadSpriteSheet(filename: String, assetName: String, horizontalCells: Int, verticalCells: Int): SpriteSheet
        = SpriteSheet(filename, assetName, horizontalCells, verticalCells).also{ add(it) }

    override fun loadFont(filename: String, assetName: String, fontSizes: FloatArray): Font
        = Font(filename, assetName, fontSizes).also { add(it) }

    override fun loadCursor(filename: String, assetName: String, xHotSpot: Int, yHotSpot: Int): Cursor
        = Cursor(filename, assetName, xHotSpot, yHotSpot).also { add(it) }

    override fun loadSound(filename: String, assetName: String): Sound
        = Sound(filename, assetName).also { add(it)  }

    override fun loadText(filename: String, assetName: String): Text
        = Text(filename, assetName).also { add(it) }

    override fun loadBinary(filename: String, assetName: String): Binary
        = Binary(filename, assetName).also { add(it) }

    override fun loadAllTextures(directory: String) =
        getResourceFilenames(directory)
            .forEachFiltered(
                { filename -> Texture.SUPPORTED_FORMATS.any { filename.endsWith(it) } },
                { filename -> loadTexture("$directory/$filename", filename.substringBeforeLast(".")) })

    private fun getResourceFilenames(path: String): List<String> =
        AssetsImpl::class.java.getResourceAsStream(path)
            ?.let { BufferedReader(InputStreamReader(it)).readLines() }
            ?: emptyList()

    override fun loadInitialAssets()
    {
        val startTime = System.nanoTime()
        add(Font.DEFAULT)
        runBlocking {
            assets.values.forEach {
                launch {
                    it.load()
                }
            }
        }
        assets.values.forEach(onAssetLoadedCallback)
        initialAssetsLoaded = true
        Logger.debug("Loaded ${assets.size} assets in ${(System.nanoTime() - startTime) / 1_000_000} ms. [${assets.values.joinToString { it.name }}]")
    }

    override fun <T: Asset> add(asset: T): T
    {
        if (assets.containsKey(asset.name))
            Logger.warn("Asset with name: ${asset.name} already exists and will be overridden")

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
        Logger.info("Cleaning up assets...")
        assets.values.forEach { it.delete() }
    }
}