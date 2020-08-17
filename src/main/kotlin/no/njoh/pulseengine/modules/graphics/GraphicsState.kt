package no.njoh.pulseengine.modules.graphics

class GraphicsState
{
    lateinit var textureArray: TextureArray

    fun cleanup()
    {
        textureArray.cleanup()
    }
}