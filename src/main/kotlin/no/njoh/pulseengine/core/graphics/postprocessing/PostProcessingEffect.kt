package no.njoh.pulseengine.core.graphics.postprocessing

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Attachment
import no.njoh.pulseengine.core.graphics.api.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.api.objects.FrameBufferObject
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.renderers.FullFrameRenderer
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

interface PostProcessingEffect
{
    val name: String
    fun init()
    fun process(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    fun getTexture(index: Int): Texture?
    fun destroy()
}

abstract class SinglePassEffect(
    var textureFormat: TextureFormat = TextureFormat.RGBA8,
    var textureFilter: TextureFilter = TextureFilter.LINEAR,
    val attachments: List<Attachment> = listOf(COLOR_TEXTURE_0)
) : PostProcessingEffect {

    protected lateinit var fbo: FrameBufferObject
    protected lateinit var program: ShaderProgram
    protected lateinit var renderer: FullFrameRenderer

    protected abstract fun loadShaderProgram(): ShaderProgram

    abstract fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>

    override fun init()
    {
        if (!this::program.isInitialized)
            program = loadShaderProgram()

        if (!this::renderer.isInitialized)
            renderer = FullFrameRenderer(program)

        renderer.init()
    }

    override fun process(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        if (inTextures.isEmpty())
            return inTextures

        updateFBO(inTextures.first())
        return applyEffect(engine, inTextures)
    }

    override fun getTexture(index: Int): Texture? = if (this::fbo.isInitialized) fbo.getTexture(index) else null

    private fun updateFBO(inTexture: Texture)
    {
        if (!this::fbo.isInitialized)
            fbo = FrameBufferObject.create(inTexture.width, inTexture.height, textureFormat = textureFormat, textureFilter = textureFilter, attachments = attachments)

        val fboTex = fbo.getTextures().first()

        if (
            fboTex.width  != inTexture.width ||
            fboTex.height != inTexture.height ||
            fboTex.filter != textureFilter ||
            fboTex.format != textureFormat
        ) {
            fbo.delete()
            fbo = FrameBufferObject.create(inTexture.width, inTexture.height, textureFormat = textureFormat, textureFilter = textureFilter, attachments = attachments)
        }
    }

    override fun destroy()
    {
        if (this::program.isInitialized) program.delete()
        if (this::renderer.isInitialized) renderer.destroy()
        if (this::fbo.isInitialized) fbo.delete()
    }
}

abstract class MultiPassEffect(
    val numberOfRenderPasses: Int,
    var textureFormat: TextureFormat = TextureFormat.RGBA8,
    var textureFilter: TextureFilter = TextureFilter.LINEAR,
    var attachments: List<Attachment> = listOf(COLOR_TEXTURE_0)
) : PostProcessingEffect {

    protected val fbo = mutableListOf<FrameBufferObject>()
    protected val renderers = mutableListOf<FullFrameRenderer>()
    protected val programs = mutableListOf<ShaderProgram>()

    protected abstract fun loadShaderPrograms(): List<ShaderProgram>

    abstract fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>

    override fun init()
    {
        if (programs.isEmpty())
            programs.addAll(loadShaderPrograms())

        if (renderers.isEmpty())
            renderers.addAll(programs.map { FullFrameRenderer(it) })

        renderers.forEachFast { it.init() }
    }

    override fun process(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        if (inTextures.isEmpty())
            return inTextures

        updateFBO(inTextures.first())
        return applyEffect(engine, inTextures)
    }

    private fun updateFBO(inTexture: Texture)
    {
        if (fbo.isEmpty())
            fbo.addAll(createNewFBOList(numberOfRenderPasses, inTexture.width, inTexture.height))

        val fboTex = fbo.first().getTextures().first()

        if (fboTex.width  != inTexture.width ||
            fboTex.height != inTexture.height ||
            fboTex.filter != textureFilter ||
            fboTex.format != textureFormat
        ) {
            fbo.forEachFast { it.delete() }
            fbo.clear()
            fbo.addAll(createNewFBOList(numberOfRenderPasses, inTexture.width, inTexture.height))
        }
    }

    private fun createNewFBOList(amount: Int, width: Int, height: Int): List<FrameBufferObject> =
        0.until(amount).map { FrameBufferObject.create(width, height, textureFormat = textureFormat, textureFilter = textureFilter, attachments = attachments) }

    override fun getTexture(index: Int): Texture? = fbo.lastOrNull()?.getTexture(index)

    override fun destroy()
    {
        programs.forEachFast { it.delete() }
        renderers.forEachFast { it.destroy() }
        fbo.forEachFast { it.delete() }
    }
}
