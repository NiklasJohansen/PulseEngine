package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.api.objects.FrameBufferObject

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
    fun cleanUp()
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

        fbo = FrameBufferObject.create(width, height, textureScale, textureFormat, textureFilter, NONE, attachments)
    }

    override fun begin() = fbo.bind()
    override fun end() = fbo.release()
    override fun getTexture(index: Int) = fbo.getTexture(index)
    override fun getTextures() = fbo.getTextures()
    override fun cleanUp() = fbo.delete()
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

        msFbo = FrameBufferObject.create(width, height, textureScale, textureFormat, textureFilter, multisampling, attachments)
        fbo = FrameBufferObject.create(width, height, textureScale, textureFormat, textureFilter, NONE, attachments)
    }

    override fun begin() =
        msFbo.bind()

    override fun end()
    {
        msFbo.release()
        msFbo.resolveToFBO(fbo)
    }

    override fun getTexture(index: Int) =
        fbo.getTexture(index)

    override fun getTextures() =
        fbo.getTextures()

    override fun cleanUp()
    {
        msFbo.delete()
        fbo.delete()
    }
}