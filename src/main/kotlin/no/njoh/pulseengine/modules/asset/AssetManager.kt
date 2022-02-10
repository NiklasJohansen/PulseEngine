package no.njoh.pulseengine.modules.asset

import no.njoh.pulseengine.data.assets.*
import kotlin.reflect.KClass

abstract class AssetManager
{
    abstract fun <T : Asset> add(asset: T): T
    abstract fun <T : Asset> get(assetName: String): T
    abstract fun <T : Asset> getAll(type: Class<T>): List<T>

    inline fun <reified T : Asset> getSafe(assetName: String): T? = getSafe(assetName, T::class)
    @PublishedApi internal abstract fun <T : Asset> getSafe(assetName: String, type: KClass<T>): T?

    abstract fun loadAllTextures(directory: String)
    abstract fun loadTexture(fileName: String, assetName: String): Texture
    abstract fun loadSpriteSheet(fileName: String, assetName: String, horizontalCells: Int, verticalCells: Int): SpriteSheet
    abstract fun loadFont(fileName: String, assetName: String, fontSizes: FloatArray): Font
    abstract fun loadCursor(fileName: String, assetName: String, xHotSpot: Int, yHotSpot: Int): Cursor
    abstract fun loadSound(fileName: String, assetName: String): Sound
    abstract fun loadText(fileName: String, assetName: String): Text
    abstract fun loadBinary(fileName: String, assetName: String): Binary
}

abstract class AssetManagerInternal : AssetManager()
{
    abstract fun loadInitialAssets()
    abstract fun setOnAssetLoaded(callback: (Asset) -> Unit)
    abstract fun setOnAssetRemoved(callback: (Asset) -> Unit)
    abstract fun cleanUp()
}

