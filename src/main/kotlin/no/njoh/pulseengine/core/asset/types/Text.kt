package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.loadText

@Icon("TEXT")
class Text(fileName: String, override val name: String) : Asset(name, fileName)
{
    lateinit var text: String
        private set

    override fun load()
    {
        this.text = fileName.loadText() ?: run {
            Logger.error("Failed to find and load Text asset: ${this.fileName}")
            ""
        }
    }

    override fun delete() { }
}