package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.AntiAliasingType.NONE
import no.njoh.pulseengine.modules.graphics.objects.FrameBufferObject
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_MULTISAMPLE

interface RenderTarget
{
    var textureScale: Float
    var textureFormat: TextureFormat
    var textureFilter: TextureFilter
    fun init(width: Int, height: Int)
    fun begin()
    fun end()
    fun getTexture(): Texture
    fun cleanUp()
}

class OffScreenRenderTarget(
    override var textureScale: Float,
    override var textureFormat: TextureFormat,
    override var textureFilter: TextureFilter
) : RenderTarget {
    private lateinit var fbo: FrameBufferObject

    override fun init(width: Int, height: Int)
    {
        if (this::fbo.isInitialized)
            fbo.delete()

        fbo = FrameBufferObject.create(width, height, textureScale, textureFormat, textureFilter, NONE)
    }

    override fun begin() = fbo.bind()
    override fun end() = fbo.release()
    override fun getTexture() = fbo.texture
    override fun cleanUp() = fbo.delete()
}

class MultisampledOffScreenRenderTarget(
    override var textureScale: Float,
    override var textureFormat: TextureFormat,
    override var textureFilter: TextureFilter,
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

        fbo = FrameBufferObject.create(width, height, textureScale, textureFormat, textureFilter, NONE)
        msFbo = FrameBufferObject.create(width, height, textureScale, textureFormat, textureFilter, antiAliasing)
    }

    override fun begin()
    {
        glEnable(GL_MULTISAMPLE)
        msFbo.bind()
    }

    override fun end()
    {
        msFbo.release()
        glDisable(GL_MULTISAMPLE)
    }

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