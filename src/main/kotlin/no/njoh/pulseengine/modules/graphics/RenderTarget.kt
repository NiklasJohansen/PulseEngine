package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.AntiAliasingType.NONE
import no.njoh.pulseengine.modules.graphics.objects.FrameBufferObject

interface RenderTarget
{
    var textureScale: Float
    var hdrEnabled: Boolean
    fun init(width: Int, height: Int)
    fun begin()
    fun end()
    fun getTexture(): Texture
    fun cleanUp()
}

class OffScreenRenderTarget(
    override var textureScale: Float,
    override var hdrEnabled: Boolean
) : RenderTarget {
    private lateinit var fbo: FrameBufferObject

    override fun init(width: Int, height: Int)
    {
        if (this::fbo.isInitialized)
            fbo.delete()

        fbo = FrameBufferObject.create(width, height, textureScale, hdrEnabled, NONE)
    }

    override fun begin() = fbo.bind()
    override fun end() = fbo.release()
    override fun getTexture() = fbo.texture
    override fun cleanUp() = fbo.delete()
}

class MultisampledOffScreenRenderTarget(
    override var textureScale: Float,
    override var hdrEnabled: Boolean,
    private val antiAliasing: AntiAliasingType
) : RenderTarget {

    private lateinit var fbo: FrameBufferObject
    private lateinit var msFbo: FrameBufferObject

    override fun init(width: Int, height: Int)
    {
        if (this::fbo.isInitialized)
            fbo.delete()

        if (this::msFbo.isInitialized)
            msFbo.delete()

        fbo = FrameBufferObject.create(width, height, textureScale, hdrEnabled, NONE)
        msFbo = FrameBufferObject.create(width, height, textureScale, hdrEnabled, antiAliasing)
    }

    override fun begin() = msFbo.bind()

    override fun end() = msFbo.release()

    override fun getTexture(): Texture
    {
        msFbo.resolveToFBO(fbo)
        return fbo.texture
    }

    override fun cleanUp()
    {
        fbo.delete()
        msFbo.delete()
    }
}