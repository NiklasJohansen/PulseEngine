package no.njoh.pulseengine.core.graphics.gpu.texture

enum class TextureAnisotropy(val value: Float)
{
    OFF(1f),
    X2(2f),
    X4(4f),
    X8(8f),
    X16(16f),
    MAX(-1f);

    companion object
    {
        fun defaultFor(filter: TextureFilter) = if (filter == TextureFilter.LINEAR_MIPMAP) X8 else OFF
    }
}