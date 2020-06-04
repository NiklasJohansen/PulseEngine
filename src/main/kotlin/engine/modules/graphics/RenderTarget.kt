package engine.modules.graphics

import engine.data.Texture

class RenderTarget
{
    private lateinit var fbo: FrameBufferObject

    fun init(width: Int, height: Int)
    {
        if(this::fbo.isInitialized)
            fbo.delete()

        fbo = FrameBufferObject.create(width, height)
    }

    fun begin() = fbo.bind()
    fun end() = fbo.release()
    fun getTexture(): Texture = fbo.texture
    fun cleanUp() = fbo.delete()
}