package no.njoh.pulseengine.core.graphics.gpu.texture.multisampling

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.gpu.buffer.FrameBufferObject
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat

/** 
 * Base class for resolvers of multisampled color attachments
 */
abstract class MsaaResolver
{
    private var initialized = false
    
    open val enabled: Boolean get() = true

    fun init(engine: PulseEngineInternal)
    {
        onInit(engine)
        initialized = true
    }

    fun resolve(engine: PulseEngineInternal, sourceTex: RenderTexture, destinationFbo: FrameBufferObject)
    {
        if (!initialized) init(engine)
        onResolve(sourceTex, destinationFbo)
    }

    fun destroy()
    {
        if (!initialized) return
        initialized = false
        onDestroy()
    }

    abstract fun supportsFormat(format: TextureFormat): Boolean

    protected abstract fun onInit(engine: PulseEngineInternal)
    
    protected abstract fun onResolve(sourceTex: RenderTexture, destinationFbo: FrameBufferObject)
    
    protected abstract fun onDestroy()
}
