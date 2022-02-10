package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.modules.asset.types.Texture
import no.njoh.pulseengine.modules.graphics.api.AntiAliasing
import no.njoh.pulseengine.modules.graphics.api.AntiAliasing.NONE
import no.njoh.pulseengine.modules.graphics.api.Attachment
import no.njoh.pulseengine.modules.graphics.api.TextureFilter
import no.njoh.pulseengine.modules.graphics.api.TextureFormat
import no.njoh.pulseengine.modules.graphics.api.objects.FrameBufferObject

interface RenderTarget
{
    var textureScale: Float
    var textureFormat: TextureFormat
    var textureFilter: TextureFilter
    var attachments: List<Attachment>

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
    private lateinit var fbo: FrameBufferObject

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
    private val antiAliasing: AntiAliasing,
    override var attachments: List<Attachment>
) : RenderTarget {

    private lateinit var msFbo: FrameBufferObject
    private lateinit var fbo: FrameBufferObject

    override fun init(width: Int, height: Int)
    {
        if (this::fbo.isInitialized)
            fbo.delete()

        if (this::msFbo.isInitialized)
            msFbo.delete()

        msFbo = FrameBufferObject.create(width, height, textureScale, textureFormat, textureFilter, antiAliasing, attachments)
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