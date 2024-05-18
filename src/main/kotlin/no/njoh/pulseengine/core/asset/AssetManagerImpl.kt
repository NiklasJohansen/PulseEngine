package no.njoh.pulseengine.core.asset

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.input.CursorType
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.loadFileNames
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import no.njoh.pulseengine.core.shared.utils.Extensions.toNowFormatted
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

open class AssetManagerImpl : AssetManagerInternal()
{
    private val loadedAssets = mutableMapOf<String, Asset>()
    private val assetsToLoad = mutableListOf<Asset>(Font.DEFAULT)
    private var onAssetLoadedCallbacks = mutableListOf<(Asset) -> Unit>()
    private var onAssetRemovedCallbacks = mutableListOf<(Asset) -> Unit>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Asset> getOrNull(assetName: String, type: Class<T>): T? =
        loadedAssets[assetName]?.takeIf { it.javaClass == type } as T?

    override fun <T : Asset> getAllOfType(type: Class<T>): List<T> =
        loadedAssets.values.filterIsInstance(type)

    override fun loadTexture(fileName: String, assetName: String, filter: TextureFilter, mipLevels: Int) =
        load(Texture(fileName, assetName, filter, mipLevels))

    override fun loadSpriteSheet(fileName: String, assetName: String, horizontalCells: Int, verticalCells: Int) =
        load(SpriteSheet(fileName, assetName, horizontalCells, verticalCells))

    override fun loadFont(fileName: String, assetName: String, fontSize: Float) =
        load(Font(fileName, assetName, fontSize))

    override fun loadSound(fileName: String, assetName: String) =
        load(Sound(fileName, assetName))

    override fun loadText(fileName: String, assetName: String) =
        load(Text(fileName, assetName))

    override fun loadBinary(fileName: String, assetName: String) =
        load(Binary(fileName, assetName))

    override fun loadCursor(fileName: String, assetName: String, type: CursorType, xHotSpot: Int, yHotSpot: Int) =
        load(Cursor(fileName, assetName, type, xHotSpot, yHotSpot))

    override fun loadAllTextures(directory: String)
    {
        for (fileName in directory.loadFileNames())
        {
            if (Texture.SUPPORTED_FORMATS.none { fileName.endsWith(it) })
                continue

            val assetName = fileName.substringAfterLast("/").substringBeforeLast(".")
            if (loadedAssets.none { it.value.name == assetName } && assetsToLoad.none { it.name == assetName })
                loadTexture(fileName, assetName)
        }
    }

    override fun <T: Asset> load(asset: T)
    {
        val existingAsset = loadedAssets[asset.name]
        if (existingAsset != null)
        {
            Logger.warn("Asset with name: ${existingAsset.name} has already been loaded and will be deleted and overridden")
            existingAsset.delete()
            onAssetRemovedCallbacks.forEachFast { it(existingAsset) }
        }

        if (assetsToLoad.any { it.name == asset.name })
        {
            Logger.warn("Asset with name: ${asset.name} is already staged for loading")
            return
        }

        assetsToLoad.add(asset)
    }

    override fun delete(assetName: String)
    {
        assetsToLoad.removeWhen { it.name == assetName }
        loadedAssets.remove(assetName)?.let()
        {
            it.delete()
            onAssetRemovedCallbacks.forEachFast { callback -> callback.invoke(it) }
        }
    }

    override fun update()
    {
        if (assetsToLoad.isEmpty())
            return

        val startTime = System.nanoTime()
        runBlocking(Dispatchers.IO)
        {
            assetsToLoad.forEachFast { launch { it.load() } }
        }

        assetsToLoad.forEachFast()
        {
            loadedAssets[it.name] = it
            onAssetLoadedCallbacks.forEachFast { callback -> callback.invoke(it) }
        }

        Logger.debug("Loaded ${assetsToLoad.size} assets in ${startTime.toNowFormatted()}. [${assetsToLoad.joinToString { it.name }}]")
        assetsToLoad.clear()
    }

    override fun setOnAssetLoaded(callback: (Asset) -> Unit)
    {
        onAssetLoadedCallbacks.add(callback)
    }

    override fun setOnAssetRemoved(callback: (Asset) -> Unit)
    {
       onAssetRemovedCallbacks.add(callback)
    }

    override fun destroy()
    {
        Logger.info("Destroying assets (${this::class.simpleName})")
        loadedAssets.values.toList().forEachFast { delete(it.name) }
    }
}