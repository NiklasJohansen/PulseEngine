package no.njoh.pulseengine.modules.graphics.postprocessing

import no.njoh.pulseengine.modules.asset.types.Texture
import no.njoh.pulseengine.modules.graphics.api.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.modules.graphics.api.objects.FrameBufferObject
import no.njoh.pulseengine.modules.graphics.api.ShaderProgram
import no.njoh.pulseengine.modules.graphics.renderers.FrameTextureRenderer
import no.njoh.pulseengine.modules.shared.utils.Logger

interface PostProcessingEffect
{
    fun init()
    fun process(texture: Texture): Texture
    fun getTexture(): Texture?
    fun reloadShaders()
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

    override fun getTexture(): Texture? =
        if (this::fbo.isInitialized) fbo.getTexture() else null

    private fun updateFBO(texture: Texture)
    {
        if (!this::fbo.isInitialized)
            fbo = FrameBufferObject.create(texture.width, texture.height, attachments = listOf(COLOR_TEXTURE_0))

        if (fbo.width != texture.width || fbo.height != texture.height)
        {
            fbo.delete()
            fbo = FrameBufferObject.create(texture.width, texture.height, attachments = listOf(COLOR_TEXTURE_0))
        }
    }

    override fun reloadShaders()
    {
        runCatching { loadShaderProgram() }
            .onFailure { Logger.error("Failed to reload shaders for post processing effect ${this::class.simpleName}, reason: ${it.message}") }
            .onSuccess {
                if (this::program.isInitialized) program.delete()
                if (this::renderer.isInitialized) renderer.cleanUp()
                program = it
                renderer = FrameTextureRenderer(program)
                init()
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
    protected val renderers = mutableListOf<FrameTextureRenderer>()
    protected val programs = mutableListOf<ShaderProgram>()

    protected abstract fun loadShaderPrograms(): List<ShaderProgram>
    protected abstract fun applyEffect(texture: Texture): Texture

    override fun init()
    {
        if (programs.isEmpty())
            programs.addAll(loadShaderPrograms())

        if (renderers.isEmpty())
            renderers.addAll(programs.map { FrameTextureRenderer(it) })

        renderers.forEach { it.init() }
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

        if (fbo.first().width != texture.width || fbo.first().height != texture.height)
        {
            fbo.forEach { it.delete() }
            fbo.clear()
            fbo.addAll(createNewFBOList(numberOfRenderPasses, texture.width, texture.height))
        }
    }

    private fun createNewFBOList(amount: Int, width: Int, height: Int): List<FrameBufferObject> =
        0.until(amount).map { FrameBufferObject.create(width, height, attachments = listOf(COLOR_TEXTURE_0)) }

    override fun getTexture(): Texture? = fbo.lastOrNull()?.getTexture(0)

    override fun reloadShaders()
    {
        runCatching { loadShaderPrograms() }
            .onFailure { Logger.error("Failed to reload shaders for post processing effect ${this::class.simpleName}, reason: ${it.message}") }
            .onSuccess { newPrograms ->
                programs.forEach { it.delete() }
                renderers.forEach { it.cleanUp() }
                programs.clear()
                renderers.clear()
                programs.addAll(newPrograms)
                init()
            }
    }

    override fun cleanUp()
    {
        programs.forEach { it.delete() }
        renderers.forEach { it.cleanUp() }
        fbo.forEach { it.delete() }
    }
}
