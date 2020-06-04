package engine.modules.graphics

import engine.data.Font

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