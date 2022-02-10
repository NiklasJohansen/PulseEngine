package no.njoh.pulseengine.core.asset.types

abstract class Asset(
    open val name: String,
    protected val fileName: String
) {
    abstract fun load()
    abstract fun delete()
}