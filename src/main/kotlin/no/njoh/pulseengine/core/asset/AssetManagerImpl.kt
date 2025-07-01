package no.njoh.pulseengine.core.asset

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.loadFileNames
import no.njoh.pulseengine.core.shared.utils.Extensions.pathToAsset
import no.njoh.pulseengine.core.shared.utils.Extensions.toNowFormatted

open class AssetManagerImpl : AssetManagerInternal()
{
    private val assets = mutableMapOf<String, Asset>()
    private val assetsToLoad = mutableListOf<Asset>(Font.DEFAULT)
    private val assetsToUnload = mutableListOf<Asset>()
    private val assetsToReload = mutableListOf<Asset>()
    private var onAssetLoadedCallbacks = mutableListOf<(Asset) -> Unit>()
    private var onAssetUnloadedCallbacks = mutableListOf<(Asset) -> Unit>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Asset> getOrNull(assetName: String, type: Class<T>): T? =
        assets[assetName]?.takeIf { it.javaClass == type || type.isAssignableFrom(it.javaClass) } as T?

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
            assets[asset.name] = asset
            notifyAssetLoaded(asset)
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
        assetsToUnload += assets.remove(assetName) ?: return
    }

    override fun update()
    {
        handleAssetUnloading()
        handleAssetLoading()
        handleAssetReloading()
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
        assets.forEach { (_, asset) ->
            if (filePath.endsWith(asset.filePath))
            {
                asset.filePath = filePath
                reload(asset)
                return
            }
        }

        pathToAsset(filePath)?.let() // If the asset is new and not loaded, try to load it
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

    private fun handleAssetLoading()
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
            Logger.debug { "Loaded ${assetsToLoad.size} assets in ${startTime.toNowFormatted()}. [${assetsToLoad.joinToString { it.name }}]" }
        }
        else assetsToLoad[0].load()

        assetsToLoad.forEachFast()
        {
            assets[it.name] = it
            runCatching { notifyAssetLoaded(it) }.onFailure { error -> Logger.error { "onAssetLoadedCallback failed for asset: ${it.name}, reason: ${error.message}" } }
        }
        assetsToLoad.clear()
    }

    private fun handleAssetReloading()
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
                Logger.debug { "Reloaded asset: ${it.filePath}" }
            }
            catch (e: Exception) { Logger.error { "Failed to reload asset: ${it.name}, reason: ${e.message}" } }
        }
        assetsToReload.clear()
    }

    private fun notifyAssetLoaded(asset: Asset) = onAssetLoadedCallbacks.forEachFast { callback -> callback(asset) }

    private fun notifyAssetUnloaded(asset: Asset) = onAssetUnloadedCallbacks.forEachFast { callback -> callback(asset) }
}