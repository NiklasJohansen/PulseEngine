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

abstract class SinglePassEffect : PostProcessingEffect
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

abstract class MultiPassEffect(private val numberOfRenderPasses: Int) : PostProcessingEffect
{
    protected val fbo = mutableListOf<FrameBufferObject>()
    protected val render = mutableListOf<FrameTextureRenderer>()
    protected val program = mutableListOf<ShaderProgram>()

    protected abstract fun acquireShaderPrograms(): List<ShaderProgram>
    protected abstract fun applyEffect(texture: Texture): Texture

    override fun init()
    {
        if(program.isEmpty())
            program.addAll(acquireShaderPrograms())

        if(render.isEmpty())
            render.addAll(program.map { FrameTextureRenderer(it) })

        render.forEach { it.init() }
    }

    override fun process(texture: Texture): Texture
    {
        updateFBO(texture)
        return applyEffect(texture)
    }

    private fun updateFBO(texture: Texture)
    {
        if (fbo.isEmpty())
            fbo.addAll(createNewFBOList(numberOfRenderPasses, texture.width, texture.height))

        if (fbo.first().texture.width != texture.width || fbo.first().texture.height != texture.height)
        {
            fbo.forEach { it.delete() }
            fbo.clear()
            fbo.addAll(createNewFBOList(numberOfRenderPasses, texture.width, texture.height))
        }
    }

    private fun createNewFBOList(amount: Int, width: Int, height: Int): List<FrameBufferObject> =
        0.until(amount).map { FrameBufferObject.create(width, height) }

    override fun cleanUp()
    {
        program.forEach { it.delete() }
        render.forEach { it.cleanUp() }
        fbo.forEach { it.delete() }
    }
}
