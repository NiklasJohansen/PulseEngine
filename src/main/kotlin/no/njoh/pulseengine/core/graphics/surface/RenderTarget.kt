package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.gpu.buffer.FrameBufferObject
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureDescriptor
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import kotlin.collections.plusAssign

class RenderTarget(val textureDescriptors: List<TextureDescriptor>)
{
    private val hasMultisampling = textureDescriptors.anyMatches { it.multisampling != Multisampling.NONE }
    private val frameBuffers = mutableListOf<FrameBufferObject>()
    private val writeFbo get() = frameBuffers.first() // MSAA or single sampled
    private val readFbo  get() = frameBuffers.last()  // Single sampled 

    fun init(width: Int, height: Int)
    {
        frameBuffers.forEachFast { it.destroy() }
        frameBuffers.clear()

        // The first frame buffer can have multisampling
        frameBuffers += FrameBufferObject.create(width, height, textureDescriptors)

        // But make sure the last frame buffer is the one without multisampling
        if (hasMultisampling)
            frameBuffers += FrameBufferObject.create(width, height, textureDescriptors.map { it.copy(multisampling = Multisampling.NONE) })
    }

    fun begin() = writeFbo.bind()

    fun end()
    {
        writeFbo.release()

        if (hasMultisampling)
        {
            val writeTex = writeFbo.getTexture(0)
            measure(id = "resolve_fbo", label = { "Resolve: " plus writeTex.name plus " (" plus writeTex.multisampling plus ")" })
            {
                writeFbo.resolveToFBO(readFbo)
            }
        }
    }

    fun resolveDepth(engine: PulseEngineInternal) 
    {
        if (hasMultisampling)
        {
            val writeTex = writeFbo.getTexture(0)
            measure(id = "resolve_depth_fbo", label = { "Resolve depth: " plus writeTex.name plus " (" plus writeTex.multisampling plus ")"  })
            {
                writeFbo.resolveDepthToFBO(readFbo)
            }
        }
    }

    fun generateMips(engine: PulseEngineInternal) = getTextures().forEachFast { it.generateMips(engine) }

    fun getTexture(index: Int) = readFbo.getTextureOrNull(index)

    fun getTextures() = readFbo.getTextures()

    fun getFbo() = readFbo

    fun destroy() = frameBuffers.forEachFast { it.destroy() }
}