package no.njoh.pulseengine.core.asset

import gnu.trove.map.hash.THashMap
import gnu.trove.map.hash.TObjectIntHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.ResourceResolver
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.loadFileNames
import no.njoh.pulseengine.core.shared.utils.Extensions.pathToAsset
import no.njoh.pulseengine.core.shared.utils.Extensions.toNowFormatted
import no.njoh.pulseengine.core.shared.utils.ResourceResolver.matchesPath

open class AssetManagerImpl : AssetManagerInternal()
{
    private val assets = THashMap<String, Asset>()
    private val slotsByName = TObjectIntHashMap<String>(64, 0.5f, INVALID_ASSET_SLOT)
    private var assetsBySlot = arrayOfNulls<Asset>(64)
    private var nextAssetSlot = 0
    private val assetsToLoad = mutableListOf<Asset>(Font.DEFAULT)
    private val assetsToUnload = mutableListOf<Asset>()
    private val assetsToReload = mutableListOf<Asset>()
    private val subAssetsToLoad = mutableListOf<Asset>()
    private var onAssetLoadedCallbacks = mutableListOf<(Asset) -> Unit>()
    private var onAssetUnloadedCallbacks = mutableListOf<(Asset) -> Unit>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Asset> getOrNull(assetName: String, type: Class<T>): T? =
        assets[assetName]?.takeIf { it.javaClass == type || type.isAssignableFrom(it.javaClass) } as T?

    override fun <T : Asset> getOrNull(handle: AssetHandle<T>, type: Class<T>): T?
    {
        var slot = handle.slot
        if (slot == INVALID_ASSET_SLOT)
        {
            slot = getOrCreateSlot(handle.name)
            handle.slot = slot
        }

        val asset = assetsBySlot[slot] ?: return null

        @Suppress("UNCHECKED_CAST")
        if (asset.javaClass == type || type.isAssignableFrom(asset.javaClass))
            return asset as T
 
        return null
    }

    override fun <T : Asset> getAllOfType(type: Class<T>): List<T> =
        assets.values.filterIsInstance(type)

    override fun load(asset: Asset)
    {
        if (assets.containsKey(asset.name))
            return // Already loaded

        if (assetsToLoad.any { it.name == asset.name })
            return // Already staged for loading

        assetsToLoad += asset
    }

    override fun loadAll(directory: String, toAsset: (filePath: String) -> Asset?)
    {
        directory.loadFileNames().forEachFast { filePath -> toAsset(filePath)?.let { load(it) } }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Asset> loadNow(asset: T): T
    {
        val loadedAsset = assets[asset.name]
        if (loadedAsset != null && loadedAsset::class == asset::class)
            return loadedAsset as T

        try
        {
            asset.load()
            register(asset)
            notifyAssetLoaded(asset)
            asset.getSubAssets().forEachFast { loadNow(it) }
        }
        catch (e: Exception) { Logger.error { "Failed to load asset (now): ${asset.name}, reason: ${e.message}" } }

        return asset
    }

    override fun reload(asset: Asset)
    {
        assetsToReload += asset
    }

    override fun unload(assetName: String)
    {
        val asset = assets.remove(assetName) ?: return

        val slot = slotsByName[asset.name]
        if (assetsBySlot.getOrNull(slot) === asset)
            assetsBySlot[slot] = null

        assetsToUnload += asset
    }

    override fun update(engine: PulseEngineInternal)
    {
        handleAssetUnloading()
        handleAssetReloading(engine)
        handleAssetLoading(engine)
    }

    override fun setOnAssetLoaded(callback: (Asset) -> Unit)
    {
        onAssetLoadedCallbacks.add(callback)
    }

    override fun setOnAssetUnloaded(callback: (Asset) -> Unit)
    {
       onAssetUnloadedCallbacks.add(callback)
    }

    override fun reloadAssetFromPath(filePath: String)
    {
        var foundLoadedAsset = false
        val resourcePath = ResourceResolver.toResourcePath(filePath)
        assets.forEach { (_, asset) ->
            if (asset.filePath.isNotEmpty() && matchesPath(asset.filePath, filePath))
            {
                asset.filePath = resourcePath
                reload(asset)
                foundLoadedAsset = true
            }
        }

        if (foundLoadedAsset) return

        pathToAsset(resourcePath)?.let() // If the asset is new and not loaded, try to load it
        {
            val toLoadCount = assetsToLoad.size
            load(it)
            if (assetsToLoad.size != toLoadCount)
                Logger.info { "Loaded new asset from path: $filePath" }
        }
    }

    override fun destroy()
    {
        Logger.info { "Destroying assets (${this::class.simpleName})" }
        assets.values.toList().forEachFast { unload(it.name) }
    }

    private fun handleAssetUnloading()
    {
        if (assetsToUnload.isEmpty()) return

        assetsToUnload.forEachFast()
        {
            try
            {
                it.unload()
                notifyAssetUnloaded(it)
            }
            catch (e: Exception) { Logger.error { "Failed to unload asset: ${it.name}, reason: ${e.message}" } }
        }
        assetsToUnload.clear()
    }

    private fun handleAssetLoading(engine: PulseEngineInternal)
    {
        if (assetsToLoad.isEmpty()) return

        val assetCount = assetsToLoad.size
        val startTime = System.nanoTime()
        var loadTimeNanos = 0L
        var batchStart = 0

        while (batchStart < assetCount)
        {
            val batchEnd = minOf(batchStart + ASSET_LOAD_BATCH_SIZE, assetCount)
            val loadStartTime = System.nanoTime()

            if (batchEnd - batchStart > 1)
            {
                runBlocking()
                {
                    var i = batchStart
                    while (i < batchEnd)
                    {
                        val asset = assetsToLoad[i++]
                        launch(ASSET_LOAD_DISPATCHER) { loadAsset(asset) }
                    }
                }
            }
            else loadAsset(assetsToLoad[batchStart])

            loadTimeNanos += System.nanoTime() - loadStartTime

            var i = batchStart
            while (i < batchEnd)
            {
                val asset = assetsToLoad[i++]
                subAssetsToLoad += asset.getSubAssets()
                register(asset)
                runCatching { notifyAssetLoaded(asset) }.onFailure { error -> Logger.error { "onAssetLoadedCallback failed for asset: ${asset.name}, reason: ${error.message}" } }
                asset.postProcess(engine)
            }

            batchStart = batchEnd
        }

        if (assetCount > 1)
        {
            val loadTime = "%.3f ms".format(loadTimeNanos.toDouble() * 1e-6)
            Logger.info { "Loaded and initialized $assetCount assets in ${startTime.toNowFormatted()} (loading: $loadTime). [${assetsToLoad.subList(0, assetCount).joinToString { it.name }}]" }
        }

        assetsToLoad.clear()
        assetsToLoad += subAssetsToLoad
        subAssetsToLoad.clear()
    }

    private fun loadAsset(asset: Asset)
    {
        runCatching { asset.load() }.onFailure { error -> Logger.error { "Failed to load asset: ${asset.name}, reason: ${error.message}" } }
    }

    private fun handleAssetReloading(engine: PulseEngineInternal)
    {
        if (assetsToReload.isEmpty()) return

        assetsToReload.forEachFast()
        {
            try
            {
                val previousSubAssetNames = it.getSubAssets().map { it.name }

                it.unload()
                notifyAssetUnloaded(it)
                it.load()
                notifyAssetLoaded(it)
                it.postProcess(engine)

                previousSubAssetNames.forEachFast { name -> unload(name) }
                it.getSubAssets().forEachFast { subAsset -> load(subAsset) }

                Logger.info { "Reloaded asset: ${it.filePath}" }
            }
            catch (e: Exception) { Logger.error { "Failed to reload asset: ${it.name}, reason: ${e.message}" } }
        }
        assetsToReload.clear()
    }

    private fun notifyAssetLoaded(asset: Asset) = onAssetLoadedCallbacks.forEachFast { callback -> callback(asset) }

    private fun notifyAssetUnloaded(asset: Asset) = onAssetUnloadedCallbacks.forEachFast { callback -> callback(asset) }

    private fun register(asset: Asset)
    {
        val slot = getOrCreateSlot(asset.name)
        assets[asset.name] = asset
        assetsBySlot[slot] = asset
    }

    private fun getOrCreateSlot(assetName: String): Int
    {
        val existingSlot = slotsByName[assetName]
        if (existingSlot != INVALID_ASSET_SLOT)
            return existingSlot

        val newSlot = nextAssetSlot++
        slotsByName.put(assetName, newSlot)

        val requiredCapacity = newSlot + 1
        if (requiredCapacity > assetsBySlot.size)
        {
            var newCapacity = assetsBySlot.size
            while (newCapacity < requiredCapacity)
                newCapacity *= 2
            assetsBySlot = assetsBySlot.copyOf(newCapacity)
        }

        return newSlot
    }

    private companion object
    {
        const val MAX_ASSET_LOAD_BATCH_SIZE = 8
        val ASSET_LOAD_BATCH_SIZE = Runtime.getRuntime().availableProcessors().coerceIn(2, MAX_ASSET_LOAD_BATCH_SIZE)
        val ASSET_LOAD_DISPATCHER = Dispatchers.Default.limitedParallelism(ASSET_LOAD_BATCH_SIZE)
    }
}