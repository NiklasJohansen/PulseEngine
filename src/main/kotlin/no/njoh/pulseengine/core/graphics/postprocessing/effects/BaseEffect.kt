package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Texture
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

    protected val fbo;      get() = frameBuffers[0]
    protected val renderer; get() = renderers[0]
    protected val program;  get() = programs[0]

    constructor(vararg textureDescriptors: TextureDescriptor, numFrameBufferObjects: Int = 1) : this(textureDescriptors.toList(), numFrameBufferObjects)

    open fun loadShaderProgram(): ShaderProgram = throw NotImplementedError("You must override either loadShaderProgram or loadShaderPrograms")
    open fun loadShaderPrograms(): List<ShaderProgram> = listOf(loadShaderProgram())

    abstract fun applyEffect(engine: PulseEngineInternal, inTextures: List<Texture>): List<Texture>

    override fun init()
    {
        if (programs.isEmpty())
            programs.addAll(loadShaderPrograms())

        if (renderers.isEmpty())
            programs.forEachFast { renderers.add(FullFrameRenderer(it)) }

        renderers.forEachFast { it.init() }
    }

    override fun process(engine: PulseEngineInternal, inTextures: List<Texture>): List<Texture>
    {
        if (inTextures.isEmpty())
            return inTextures

        updateFrameBuffers(inTextures.first())

        return applyEffect(engine, inTextures)
    }

    private fun updateFrameBuffers(inTexture: Texture)
    {
        if (frameBuffers.isEmpty())
            createFrameBuffers(inTexture.width, inTexture.height)

        if (!fbo.matches(inTexture.width, inTexture.height, textureDescriptors))
        {
            frameBuffers.forEachFast { it.delete() }
            frameBuffers.clear()
            createFrameBuffers(inTexture.width, inTexture.height)
        }
    }

    private fun createFrameBuffers(width: Int, height: Int)
    {
        for (i in 0 until numFrameBufferObjects)
        {
            frameBuffers.add(FrameBufferObject.create(width, height, textureDescriptors))
        }
    }

    override fun getTexture(index: Int): Texture? = frameBuffers.lastOrNull()?.getTexture(index)

    override fun destroy()
    {
        programs.forEachFast { it.delete() }
        renderers.forEachFast { it.destroy() }
        frameBuffers.forEachFast { it.delete() }
    }
}