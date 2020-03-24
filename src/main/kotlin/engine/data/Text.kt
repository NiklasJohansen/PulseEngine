package engine.data

import engine.modules.Asset

class Text(fileName: String, override val name: String) : Asset(name, fileName)
{
    lateinit var text: String
        private set

    override fun load()
    {
        this.text = Text::class.java.getResource(fileName).readText()
    }

    override fun delete() { }

}