package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.Font

class GraphicsState
{
    lateinit var defaultFont: Font
    lateinit var textureArray: TextureArray

    fun cleanup()
    {
        defaultFont.delete()
        textureArray.cleanup()
    }
}