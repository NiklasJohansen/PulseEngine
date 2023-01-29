package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytes

@ScnIcon("FILE")
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