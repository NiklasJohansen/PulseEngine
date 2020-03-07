package engine.data

import engine.modules.Asset

data class Binary(override val name: String, val bytes: ByteArray) : Asset(name)
{
    companion object
    {
        fun create(fileName: String, assetName: String): Binary
            = Binary(assetName, Binary::class.java.getResource(fileName).readBytes())
    }

    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Binary

        if (name != other.name) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int
    {
        var result = name.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}