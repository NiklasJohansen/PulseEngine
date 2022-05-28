package no.njoh.pulseengine.core.asset

import no.njoh.pulseengine.core.asset.types.*
import kotlin.reflect.KClass

abstract class AssetManager
{
    /**
     * Adds the [asset] to the [AssetManager] and returns it.
     */
    abstract fun <T : Asset> add(asset: T): T

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
    abstract fun loadTexture(fileName: String, assetName: String): Texture

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [SpriteSheet].
     */
    abstract fun loadSpriteSheet(fileName: String, assetName: String, horizontalCells: Int, verticalCells: Int): SpriteSheet

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Font].
     */
    abstract fun loadFont(fileName: String, assetName: String, fontSizes: FloatArray): Font

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Cursor].
     */
    abstract fun loadCursor(fileName: String, assetName: String, xHotSpot: Int, yHotSpot: Int): Cursor

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Sound].
     */
    abstract fun loadSound(fileName: String, assetName: String): Sound

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Text].
     */
    abstract fun loadText(fileName: String, assetName: String): Text

    /**
     * Loads the file with given [fileName] and ads it to the [AssetManager] as a [Binary].
     */
    abstract fun loadBinary(fileName: String, assetName: String): Binary

    // Internal abstract versions of the public inline functions
    @PublishedApi internal abstract fun <T : Asset> getAllOfType(type: KClass<T>): List<T>
    @PublishedApi internal abstract fun <T : Asset> getOrNull(assetName: String, type: KClass<T>): T?
}

abstract class AssetManagerInternal : AssetManager()
{
    abstract fun loadInitialAssets()
    abstract fun setOnAssetLoaded(callback: (Asset) -> Unit)
    abstract fun setOnAssetRemoved(callback: (Asset) -> Unit)
    abstract fun cleanUp()
}

