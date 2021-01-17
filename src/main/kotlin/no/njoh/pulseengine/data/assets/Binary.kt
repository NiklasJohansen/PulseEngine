package no.njoh.pulseengine.data.assets

import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.loadBytes

class Binary(fileName: String, override val name: String) : Asset(name, fileName)
{
    lateinit var bytes: ByteArray
        private set

    override fun load()
    {
        this.bytes = fileName.loadBytes() ?: run {
            Logger.error("Failed to find and load Binary file: $fileName")
            ByteArray(0)
        }
    }

    override fun delete() { }
}