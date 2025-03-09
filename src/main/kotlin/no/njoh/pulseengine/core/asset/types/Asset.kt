package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.annotations.Icon

@Icon("BOX")
abstract class Asset(
    open var filePath: String,
    open val name: String
) {
    abstract fun load()
    abstract fun unload()
}