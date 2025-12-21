package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.shared.annotations.Icon

@Icon("BOX")
abstract class Asset(
    open var filePath: String,
    open val name: String
) {
    /**
     * Loads the asset from disk.
     */
    abstract fun load()

    /**
     * Unloads the asset from memory.
     */
    abstract fun unload()

    /**
     * Called from the engine thread after the asset has been loaded and the asset manager has notified the engine.
     */
    open fun postProcess(engine: PulseEngineInternal) {}

    /**
     * Returns a list of sub assets that are loaded as part of this asset.
     * For example, a mesh asset may contain multiple texture assets as sub assets.
     */
    open fun getSubAssets(): List<Asset> = emptyList()
}