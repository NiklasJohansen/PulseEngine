package no.njoh.pulseengine.core.asset

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.loadFileNames
import no.njoh.pulseengine.core.shared.utils.Extensions.toNowFormatted
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

open class AssetManagerImpl : AssetManagerInternal()
{
    private val assets = mutableMapOf<String, Asset>()
    private var initialAssetsLoaded = false
    private var onAssetLoadedCallbacks = mutableListOf<(Asset) -> Unit>()
    private var onAssetRemovedCallbacks = mutableListOf<(Asset) -> Unit>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Asset> getOrNull(assetName: String, type: KClass<T>): T? =
        assets[assetName]?.let { type.safeCast(it) }

    override fun <T : Asset> getAllOfType(type: KClass<T>): List<T> =
        assets.values.filterIsInstance(type.java)

    override fun loadTexture(fileName: String, assetName: String): Texture =
        Texture(fileName, assetName).also { add(it)  }

    override fun loadSpriteSheet(fileName: String, assetName: String, horizontalCells: Int, verticalCells: Int): SpriteSheet =
        SpriteSheet(fileName, assetName, horizontalCells, verticalCells).also{ add(it) }

    override fun loadFont(fileName: String, assetName: String, fontSize: Float): Font =
        Font(fileName, assetName, fontSize).also { add(it) }

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
            .filter { fileName -> Texture.SUPPORTED_FORMATS.any { fileName.endsWith(it) } }
            .forEachFast { fileName ->
                val assetName = fileName.substringAfterLast("/").substringBeforeLast(".")
                if (assets.none { it.value.name == assetName })
                    loadTexture(fileName, assetName)
            }

    override fun loadInitialAssets()
    {
        val startTime = System.nanoTime()
        add(Font.DEFAULT)
        runBlocking(Dispatchers.IO)
        {
            assets.values.forEach()
            {
                if (it !is Sound)
                    launch { it.load() }
            }
        }
        // Sound assets need to be loaded in main thread
        assets.values.filterIsInstance<Sound>().forEachFast { it.load() }
        assets.values.forEach { asset -> onAssetLoadedCallbacks.forEachFast { it.invoke(asset)  } }
        initialAssetsLoaded = true
        Logger.debug("Loaded ${assets.size} assets in ${startTime.toNowFormatted()}. [${assets.values.joinToString { it.name }}]")
    }

    override fun <T: Asset> add(asset: T): T
    {
        val existingAsset = assets[asset.name]
        if (existingAsset != null)
        {
            Logger.warn("Asset with name: ${existingAsset.name} already exists and will be deleted and overridden")
            existingAsset.delete()
        }

        if (initialAssetsLoaded)
        {
            asset.load()
            onAssetLoadedCallbacks.forEachFast { it.invoke(asset)  }
        }

        assets[asset.name] = asset
        return asset
    }

    override fun delete(assetName: String)
    {
        assets.remove(assetName)?.delete()
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