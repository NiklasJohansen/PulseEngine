package no.njoh.pulseengine.core.asset

import no.njoh.pulseengine.core.asset.types.Asset

class NoOpAssetManager : AssetManagerInternal()
{
    override fun update() {}
    override fun destroy() {}
    override fun setOnAssetLoaded(callback: (Asset) -> Unit) {}
    override fun setOnAssetUnloaded(callback: (Asset) -> Unit) {}
    override fun <T : Asset> getAllOfType(type: Class<T>) = emptyList<T>()
    override fun <T : Asset> getOrNull(assetName: String, type: Class<T>) = null
    override fun load(asset: Asset) {}
    override fun loadAll(directory: String, toAsset: (filePath: String) -> Asset?) {}
    override fun unload(assetName: String) {}
    override fun <T : Asset> loadNow(asset: T) = asset
    override fun reload(asset: Asset) {}
    override fun reloadAssetFromPath(filePath: String) {}
}