package no.njoh.pulseengine.core.asset

import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import kotlin.reflect.KClass

abstract class AssetManager
{
    /**
     * Adds the [asset] to the [AssetManager] and returns it.
     */
    abstract fun <T : Asset> load(asset: T)

    /**
     * Removes the [Asset] with given [assetName] and calls its delete function.
     */
    abstract fun delete(assetName: String)

    /**
     * Returns the [Asset] with name [assetName] and type [T] or null if not found.
     */
    inline fun <reified T : Asset> getOrNull(assetName: String): T? = getOrNull(assetName, T::class)

    /**
     * Returns a list of all [Asset]s with given type [T].
     */
    inline fun <reified T : Asset> getAllOfType(): List<T> = getAllOfType(T::class)

    /**
     * Loads all [Texture]s in the given [directory].
     */
    abstract fun loadAllTextures(directory: String)

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Texture].
     */
    abstract fun loadTexture(fileName: String, assetName: String, filter: TextureFilter = LINEAR, mipLevels: Int = 5)

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [SpriteSheet].
     */
    abstract fun loadSpriteSheet(fileName: String, assetName: String, horizontalCells: Int, verticalCells: Int)

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Font].
     */
    abstract fun loadFont(fileName: String, assetName: String, fontSize: Float = 80f)

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Sound].
     */
    abstract fun loadSound(fileName: String, assetName: String)

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Text].
     */
    abstract fun loadText(fileName: String, assetName: String)

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Binary].
     */
    abstract fun loadBinary(fileName: String, assetName: String)

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Cursor].
     */
    abstract fun loadCursor(fileName: String, assetName: String, xHotSpot: Int, yHotSpot: Int): Cursor

    // Internal abstract versions of the public inline functions
    @PublishedApi internal abstract fun <T : Asset> getAllOfType(type: KClass<T>): List<T>
    @PublishedApi internal abstract fun <T : Asset> getOrNull(assetName: String, type: KClass<T>): T?
}

abstract class AssetManagerInternal : AssetManager()
{
    abstract fun update()
    abstract fun setOnAssetLoaded(callback: (Asset) -> Unit)
    abstract fun setOnAssetRemoved(callback: (Asset) -> Unit)
    abstract fun cleanUp()
}

