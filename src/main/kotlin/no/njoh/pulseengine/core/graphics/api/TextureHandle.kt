package no.njoh.pulseengine.core.graphics.api

@JvmInline
value class TextureHandle private constructor(private val handle: Int)
{
    val samplerIndex get() = (handle shr 16) and ((1 shl 16) - 1)
    val textureIndex get() =
        if (handle != INVALID.handle) handle and ((1 shl 16) - 1)
        else throw IllegalStateException("Accessing invalid texture handle!")

    fun toFloat() = Float.fromBits(handle)

    companion object
    {
        fun create(samplerIndex: Int, texIndex: Int) =
            TextureHandle((samplerIndex shl 16) or texIndex)

        val NONE = create(0, 1000)
        val INVALID = create(0, 1001)
    }
}
