package engine.modules.graphics.postprocessing

import engine.data.Texture
import engine.modules.graphics.FrameBufferObject
import engine.modules.graphics.ShaderProgram
import engine.modules.graphics.renderers.FrameTextureRenderer

interface PostProcessingEffect
{
    fun init()
    fun process(texture: Texture): Texture
    fun cleanUp()
}

abstract class SingleStageEffect : PostProcessingEffect
{
    protected lateinit var fbo: FrameBufferObject
    protected lateinit var shaderProgram: ShaderProgram
    protected lateinit var renderer: FrameTextureRenderer

    protected abstract fun acquireShaderProgram(): ShaderProgram
    protected abstract fun applyEffect(texture: Texture): Texture

    override fun init()
    {
        if(!this::shaderProgram.isInitialized)
            shaderProgram = acquireShaderProgram()

        if(!this::renderer.isInitialized)
            renderer = FrameTextureRenderer(shaderProgram)

        renderer.init()
    }

    override fun process(texture: Texture): Texture
    {
        updateFBO(texture)
        return applyEffect(texture)
    }

    private fun updateFBO(texture: Texture)
    {
        if (!this::fbo.isInitialized)
            fbo = FrameBufferObject.create(texture.width, texture.height)

        if (fbo.texture.width != texture.width || fbo.texture.height != texture.height)
        {
            fbo.delete()
            fbo = FrameBufferObject.create(texture.width, texture.height)
        }
    }

    override fun cleanUp()
    {
        shaderProgram.delete()
        renderer.cleanUp()
        fbo.delete()
    }
}