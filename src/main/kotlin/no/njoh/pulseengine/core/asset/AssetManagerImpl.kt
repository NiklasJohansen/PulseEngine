package no.njoh.pulseengine.core.asset

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import no.njoh.pulseengine.core.shared.utils.Extensions.loadFileNames
import kotlin.reflect.KClass

open class AssetManagerImpl : AssetManagerInternal()
{
    private val assets = mutableMapOf<String, Asset>()
    private var initialAssetsLoaded = false
    private var onAssetLoadedCallbacks = mutableListOf<(Asset) -> Unit>()
    private var onAssetRemovedCallbacks = mutableListOf<(Asset) -> Unit>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Asset> get(assetName: String): T =
        assets[assetName]?.let { it as T } ?: throw IllegalArgumentException("No asset loaded with name: $assetName")

    @Suppress("UNCHECKED_CAST")
    override fun <T : Asset> getSafe(assetName: String, type: KClass<T>): T? =
        assets[assetName]?.takeIf { it::class == type }?.let { it as T }

    override fun <T : Asset> getAll(type: Class<T>): List<T> =
        assets.values.filterIsInstance(type)

    override fun loadTexture(fileName: String, assetName: String): Texture =
        Texture(fileName, assetName).also { add(it)  }

    override fun loadSpriteSheet(fileName: String, assetName: String, horizontalCells: Int, verticalCells: Int): SpriteSheet =
        SpriteSheet(fileName, assetName, horizontalCells, verticalCells).also{ add(it) }

    override fun loadFont(fileName: String, assetName: String, fontSizes: FloatArray): Font =
        Font(fileName, assetName, fontSizes).also { add(it) }

    override fun loadCursor(fileName: String, assetName: String, xHotSpot: Int, yHotSpot: Int): Cursor =
        Cursor(fileName, assetName, xHotSpot, yHotSpot).also { add(it) }

    override fun loadSound(fileName: String, assetName: String): Sound =
        Sound(fileName, assetName).also { add(it)  }

    override fun loadText(fileName: String, assetName: String): Text =
        Text(fileName, assetName).also { add(it) }

    override fun loadBinary(fileName: String, assetName: String): Binary =
        Binary(fileName, assetName).also { add(it) }

    override fun loadAllTextures(directory: String) =
        directory
            .loadFileNames()
            .forEachFiltered(
                { fileName -> Texture.SUPPORTED_FORMATS.any { fileName.endsWith(it) } },
                { fileName -> loadTexture(fileName, fileName.substringAfterLast("/").substringBeforeLast(".")) }
            )

    override fun loadInitialAssets()
    {
        val startTime = System.nanoTime()
        add(Font.DEFAULT)
        runBlocking()
        {
            assets.values.forEach()
            {
                launch { it.load() }
            }
        }
        assets.values.forEach { asset -> onAssetLoadedCallbacks.forEachFast { it.invoke(asset)  } }
        initialAssetsLoaded = true
        Logger.debug("Loaded ${assets.size} assets in ${(System.nanoTime() - startTime) / 1_000_000} ms. [${assets.values.joinToString { it.name }}]")
    }

    override fun <T: Asset> add(asset: T): T
    {
        if (assets.containsKey(asset.name))
            Logger.warn("Asset with name: ${asset.name} already exists and will be overridden")

        assets[asset.name] = asset
        if (initialAssetsLoaded)
        {
            asset.load()
            onAssetLoadedCallbacks.forEachFast { it.invoke(asset)  }
        }
        return asset
    }

    override fun setOnAssetLoaded(callback: (Asset) -> Unit)
    {
        onAssetLoadedCallbacks.add(callback)
    }

    override fun setOnAssetRemoved(callback: (Asset) -> Unit)
    {
       onAssetRemovedCallbacks.add(callback)
    }

    override fun cleanUp()
    {
        Logger.info("Cleaning up assets (${this::class.simpleName})")
        assets.values.forEach { asset ->
            asset.delete()
            onAssetRemovedCallbacks.forEachFast { it.invoke(asset) }
        }
    }
}