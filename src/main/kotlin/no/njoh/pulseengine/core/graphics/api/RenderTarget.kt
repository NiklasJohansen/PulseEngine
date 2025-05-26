package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.graphics.api.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.api.objects.FrameBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

class RenderTarget(val textureDescriptors: List<TextureDescriptor>)
{
    private val hasMultisampling = textureDescriptors.anyMatches { it.multisampling != NONE }
    private val frameBuffers = mutableListOf<FrameBufferObject>()
    private val writeFbo; get() = frameBuffers.first()
    private val readFbo; get() = frameBuffers.last()

    fun init(width: Int, height: Int)
    {
        frameBuffers.forEachFast { it.delete() }
        frameBuffers.clear()

        // First frame buffer can have multisampling
        frameBuffers += FrameBufferObject.create(width, height, textureDescriptors)

        // But make sure last frame buffer is the one without multisampling
        if (hasMultisampling)
            frameBuffers += FrameBufferObject.create(width, height, textureDescriptors.map { it.copy(multisampling = NONE) })
    }

    fun begin() = writeFbo.bind()

    fun end()
    {
        writeFbo.release()

        if (hasMultisampling)
        {
            GpuProfiler.measure({ "RESOLVE_FBO (" plus writeFbo.getTexture(0).multisampling plus ")" })
            {
                writeFbo.resolveToFBO(readFbo)
            }
        }
    }

    fun getTexture(index: Int) = readFbo.getTextureOrNull(index)

    fun getTextures() = readFbo.getTextures()

    fun destroy() = frameBuffers.forEachFast { it.delete() }
}