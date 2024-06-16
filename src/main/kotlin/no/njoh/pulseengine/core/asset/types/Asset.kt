package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.annotations.Icon

@Icon("BOX")
abstract class Asset(
    open val name: String,
    open val fileName: String
) {
    abstract fun load()
    abstract fun delete()
}