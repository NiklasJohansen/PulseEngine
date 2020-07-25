package no.njoh.pulseengine.modules.graphics.postprocessing

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.FrameBufferObject
import no.njoh.pulseengine.modules.graphics.ShaderProgram
import no.njoh.pulseengine.modules.graphics.renderers.FrameTextureRenderer

interface PostProcessingEffect
{
    fun init()
    fun process(texture: Texture): Texture
    fun cleanUp()
}

abstract class SinglePassEffect : PostProcessingEffect
{
    protected lateinit var fbo: FrameBufferObject
    protected lateinit var program: ShaderProgram
    protected lateinit var renderer: FrameTextureRenderer

    protected abstract fun loadShaderProgram(): ShaderProgram
    protected abstract fun applyEffect(texture: Texture): Texture

    override fun init()
    {
        if (!this::program.isInitialized)
            program = loadShaderProgram()

        if (!this::renderer.isInitialized)
            renderer = FrameTextureRenderer(program)

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
        if (this::program.isInitialized) program.delete()
        if (this::renderer.isInitialized) renderer.cleanUp()
        if (this::fbo.isInitialized) fbo.delete()
    }
}

abstract class MultiPassEffect(private val numberOfRenderPasses: Int) : PostProcessingEffect
{
    protected val fbo = mutableListOf<FrameBufferObject>()
    protected val renderer = mutableListOf<FrameTextureRenderer>()
    protected val program = mutableListOf<ShaderProgram>()

    protected abstract fun loadShaderPrograms(): List<ShaderProgram>
    protected abstract fun applyEffect(texture: Texture): Texture

    override fun init()
    {
        if (program.isEmpty())
            program.addAll(loadShaderPrograms())

        if (renderer.isEmpty())
            renderer.addAll(program.map { FrameTextureRenderer(it) })

        renderer.forEach { it.init() }
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
        renderer.forEach { it.cleanUp() }
        fbo.forEach { it.delete() }
    }
}
