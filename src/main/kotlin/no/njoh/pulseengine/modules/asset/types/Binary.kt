package no.njoh.pulseengine.modules.asset.types

import no.njoh.pulseengine.modules.shared.utils.Logger
import no.njoh.pulseengine.modules.shared.utils.Extensions.loadBytes

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