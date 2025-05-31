package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.objects.FrameBufferObject
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.core.graphics.renderers.FullFrameRenderer
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

abstract class BaseEffect(
    var textureDescriptors: List<TextureDescriptor> = listOf(TextureDescriptor()),
    val numFrameBufferObjects: Int = 1,
) : PostProcessingEffect {

    protected val frameBuffers = mutableListOf<FrameBufferObject>()
    protected val renderers = mutableListOf<FullFrameRenderer>()
    protected val programs = mutableListOf<ShaderProgram>()

    val fbo;      get() = frameBuffers[0]
    val renderer; get() = renderers[0]
    val program;  get() = programs[0]

    constructor(vararg textureDescriptors: TextureDescriptor, numFrameBufferObjects: Int = 1) : this(textureDescriptors.toList(), numFrameBufferObjects)

    open fun loadShaderProgram(engine: PulseEngineInternal): ShaderProgram = throw NotImplementedError("You must override either loadShaderProgram or loadShaderPrograms")
    open fun loadShaderPrograms(engine: PulseEngineInternal): List<ShaderProgram> = listOf(loadShaderProgram(engine))

    abstract fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>

    override fun init(engine: PulseEngineInternal)
    {
        if (programs.isEmpty())
            programs.addAll(loadShaderPrograms(engine))

        if (renderers.isEmpty())
            programs.forEachFast { renderers.add(FullFrameRenderer(it)) }

        renderers.forEachFast { it.init() }
    }

    override fun process(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    {
        if (inTextures.isEmpty())
            return inTextures

        updateFrameBuffers(inTextures.first())

        return applyEffect(engine, inTextures)
    }

    private fun updateFrameBuffers(inTexture: RenderTexture)
    {
        if (frameBuffers.isEmpty())
        {
            createNewFrameBuffers(inTexture.width, inTexture.height)
        }
        else if (!fbo.matches(inTexture.width, inTexture.height, textureDescriptors))
        {
            createNewFrameBuffers(inTexture.width, inTexture.height)
        }
    }

    private fun createNewFrameBuffers(width: Int, height: Int)
    {
        frameBuffers.forEachFast { it.delete() }
        frameBuffers.clear()
        repeat(numFrameBufferObjects) { frameBuffers += FrameBufferObject.create(width, height, textureDescriptors) }
    }

    override fun getTexture(index: Int): RenderTexture? = frameBuffers.lastOrNull()?.getTextureOrNull(index)

    override fun destroy()
    {
        programs.forEachFast { it.delete() }
        renderers.forEachFast { it.destroy() }
        frameBuffers.forEachFast { it.delete() }
    }
}