package no.njoh.pulseengine.data.assets

abstract class Asset(
    open val name: String,
    protected val fileName: String
) {
    abstract fun load()
    abstract fun delete()
}