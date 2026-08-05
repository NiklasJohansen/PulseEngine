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
        handleAssetLoading(engine)
        handleAssetReloading(engine)
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
                Logger.debug { "Loaded new asset from path: $filePath" }
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

        if (assetsToLoad.size > 1)
        {
            val startTime = System.nanoTime()
            runBlocking(Dispatchers.IO)
            {
                assetsToLoad.forEachFast()
                {
                    launch { runCatching { it.load() }.onFailure { e -> Logger.error { "Failed to load asset: ${it.name}, reason: ${e.message}" } } }
                }
            }
            assetsToLoad.forEachFast { subAssetsToLoad += it.getSubAssets() }
            Logger.debug { "Loaded ${assetsToLoad.size} assets in ${startTime.toNowFormatted()}. [${assetsToLoad.joinToString { it.name }}]" }
        }
        else
        {
            assetsToLoad[0].load()
            subAssetsToLoad += assetsToLoad[0].getSubAssets()
        }

        assetsToLoad.forEachFast()
        {
            register(it)
            runCatching { notifyAssetLoaded(it) }.onFailure { error -> Logger.error { "onAssetLoadedCallback failed for asset: ${it.name}, reason: ${error.message}" } }
            it.postProcess(engine)
        }

        assetsToLoad.clear()
        assetsToLoad += subAssetsToLoad
        subAssetsToLoad.clear()
    }

    private fun handleAssetReloading(engine: PulseEngineInternal)
    {
        if (assetsToReload.isEmpty()) return

        assetsToReload.forEachFast()
        {
            try
            {
                it.unload()
                notifyAssetUnloaded(it)
                it.load()
                notifyAssetLoaded(it)
                it.postProcess(engine)
                Logger.debug { "Reloaded asset: ${it.filePath}" }
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
}