package no.njoh.pulseengine.core.graphics.gpu.texture.mipmap

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment.*
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max

/**
 * Abstract class for generating mipmaps for [RenderTexture]s.
 */
abstract class MipmapGenerator
{
    private var initialized = false

    /**
     * Initializes the mipmapping resources. Called once, but can be called again to reinitialize resources.
     */
    fun init(engine: PulseEngineInternal)
    {
        onInit(engine)
        initialized = true
    }

    /**
     * Generates mipmaps for the given RenderTexture.
     */
    fun generateMipmaps(engine: PulseEngineInternal, texture: RenderTexture)
    {
        if (texture.multisampling != Multisampling.NONE)
            return

        if (!texture.attachment.hasColor && texture.attachment != DEPTH_TEXTURE)
            return 

        if (!initialized)
        {
            onInit(engine)
            initialized = true
        }

        GpuProfiler.measure({ "RENDER_MIP_CHAIN " plus '(' plus texture.name + ')' })
        {
            onGenerate(engine, texture)
        }
    }

    /**
     * Destroys the mipmapping resources.
     */
    fun destroy()
    {
        if (!initialized) return
        initialized = false
        onDestroy()
    }

    /**
     * Returns the default number of mip levels for the given width and height.
     */
    open fun getLevelCount(width: Int, height: Int) = floor(log2(max(width, height).toDouble())).toInt() + 1

    protected abstract fun onInit(engine: PulseEngineInternal)
    protected abstract fun onGenerate(engine: PulseEngineInternal, texture: RenderTexture)
    protected abstract fun onDestroy()
}