package engine.data

import engine.modules.Asset

class Binary(fileName: String, override val name: String) : Asset(name, fileName)
{
    lateinit var bytes: ByteArray
        private set

    override fun load()
    {
        this.bytes = Binary::class.java.getResource(fileName).readBytes()
    }

    override fun delete() { }
}