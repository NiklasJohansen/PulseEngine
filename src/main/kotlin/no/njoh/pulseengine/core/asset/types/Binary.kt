package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytesFromDisk
import no.njoh.pulseengine.core.shared.utils.Logger

@Icon("FILE")
class Binary(filePath: String, name: String) : Asset(filePath, name)
{
    lateinit var bytes: ByteArray
        private set

    override fun load()
    {
        this.bytes = filePath.loadBytesFromDisk() ?: run {
            Logger.error("Failed to find and load Binary file: $filePath")
            ByteArray(0)
        }
    }

    override fun unload()
    {
        bytes = ByteArray(0)
    }
}