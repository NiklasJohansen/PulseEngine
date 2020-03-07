package engine.data

import engine.modules.Asset

data class Text(override val name: String, val text: String) : Asset(name)
{
    companion object
    {
        fun create(fileName: String, assetName: String): Text
            = Text(assetName, Text::class.java.getResource(fileName).readText())
    }
}