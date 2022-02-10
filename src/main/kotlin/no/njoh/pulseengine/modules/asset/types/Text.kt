package no.njoh.pulseengine.modules.asset.types

import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.loadText

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