package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.loadTextFromDisk
import no.njoh.pulseengine.core.shared.utils.Logger

@Icon("TEXT")
class Text(filePath: String, name: String) : Asset(filePath, name)
{
    lateinit var text: String
        private set

    override fun load()
    {
        this.text = filePath.loadTextFromDisk() ?: run {
            Logger.error("Failed to find and load Text asset: $filePath")
            ""
        }
    }

    override fun unload() { }
}