package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.api.objects.FrameBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuProfiler

interface RenderTarget
{
    var textureScale: Float
    var textureFormat: TextureFormat
    var textureFilter: TextureFilter
    var attachments: List<Attachment>
    var fbo: FrameBufferObject

    fun init(width: Int, height: Int)
    fun begin()
    fun end()
    fun getTexture(index: Int = 0): Texture?
    fun getTextures(): List<Texture>
    fun destroy()
}

class OffScreenRenderTarget(
    override var textureScale: Float,
    override var textureFormat: TextureFormat,
    override var textureFilter: TextureFilter,
    override var attachments: List<Attachment>
) : RenderTarget {

    override lateinit var fbo: FrameBufferObject

    override fun init(width: Int, height: Int)
    {
        if (this::fbo.isInitialized)
            fbo.delete()

        fbo = FrameBufferObject.create(width, height, attachments.map { TextureDescriptor(textureFormat, textureFilter, NONE, it, textureScale) } )
    }

    override fun begin() = fbo.bind()
    override fun end() = fbo.release()
    override fun getTexture(index: Int) = fbo.getTexture(index)
    override fun getTextures() = fbo.getTextures()
    override fun destroy() = fbo.delete()
}

class MultisampledOffScreenRenderTarget(
    override var textureScale: Float,
    override var textureFormat: TextureFormat,
    override var textureFilter: TextureFilter,
    private val multisampling: Multisampling,
    override var attachments: List<Attachment>
) : RenderTarget {

    override lateinit var fbo: FrameBufferObject
    private lateinit var msFbo: FrameBufferObject

    override fun init(width: Int, height: Int)
    {
        if (this::fbo.isInitialized)
            fbo.delete()

        if (this::msFbo.isInitialized)
            msFbo.delete()

        msFbo = FrameBufferObject.create(width, height, attachments.map { TextureDescriptor(textureFormat, textureFilter, multisampling, it, textureScale) })
        fbo = FrameBufferObject.create(width, height, attachments.map { TextureDescriptor(textureFormat, textureFilter, NONE, it, textureScale) })
    }

    override fun begin() =
        msFbo.bind()

    override fun end()
    {
        GpuProfiler.measure({ "RESOLVE_FBO (" plus multisampling.name plus ")" })
        {
            msFbo.release()
            msFbo.resolveToFBO(fbo)
        }
    }

    override fun getTexture(index: Int) =
        fbo.getTexture(index)

    override fun getTextures() =
        fbo.getTextures()

    override fun destroy()
    {
        msFbo.delete()
        fbo.delete()
    }
}