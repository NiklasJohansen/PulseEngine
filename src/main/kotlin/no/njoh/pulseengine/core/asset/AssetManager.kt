package no.njoh.pulseengine.core.asset

import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.shared.utils.Extensions.pathToAsset

abstract class AssetManager
{
    /**
     * Adds the [asset] into the [AssetManager] an calls the load function.
     * If the [asset] is already loaded, nothing will happen.
     */
    abstract fun load(asset: Asset)

    /**
     * Loads all [Asset]s in the given [directory] using the [toAsset] function.
     * If the assets are already loaded, nothing will happen.
     */
    abstract fun loadAll(directory: String, toAsset: ((filePath: String) -> Asset?) = ::pathToAsset)

    /**
     * Reloads the given [asset] from file by first unloading it and then loading it again.
     */
    abstract fun reload(asset: Asset)

    /**
     * Removes the [Asset] with given [assetName] and calls its unload function.
     */
    abstract fun unload(assetName: String)

    /**
     * Returns the [Asset] with name [assetName] and type [T] or null if not found.
     */
    inline fun <reified T : Asset> getOrNull(assetName: String): T? = getOrNull(assetName, T::class.java)

    /**
     * Returns the [Asset] with name [assetName] and given class type or null if not found.
     */
    abstract fun <T : Asset> getOrNull(assetName: String, type: Class<T>): T?

    /**
     * Returns a list of all [Asset]s with given type [T].
     */
    inline fun <reified T : Asset> getAllOfType(): List<T> = getAllOfType(T::class.java)

    /**
     * Returns a list of all [Asset]s with given class type.
     */
    abstract fun <T : Asset> getAllOfType(type: Class<T>): List<T>
}

abstract class AssetManagerInternal : AssetManager()
{
    abstract fun update()
    abstract fun <T : Asset> loadNow(asset: T): T
    abstract fun setOnAssetLoaded(callback: (Asset) -> Unit)
    abstract fun setOnAssetUnloaded(callback: (Asset) -> Unit)
    abstract fun reloadAssetFromPath(filePath: String)
    abstract fun destroy()
}