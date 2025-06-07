package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.objects.FrameBufferObject
import no.njoh.pulseengine.core.graphics.renderers.FullFrameRenderer
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap
import org.lwjgl.opengl.GL11.*
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
        if (!texture.attachment.isDrawable)
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

/**
 * A native mipmap generator implementation that uses OpenGL's built-in mipmap generation.
 */
class NativeMipmapGenerator : MipmapGenerator()
{
    override fun onInit(engine: PulseEngineInternal) {}
    override fun onDestroy() {}
    override fun onGenerate(engine: PulseEngineInternal, texture: RenderTexture)
    {
        glBindTexture(GL_TEXTURE_2D, texture.handle.textureIndex)
        glGenerateMipmap(GL_TEXTURE_2D)
    }
}

/**
 * A custom mipmap generator that uses a shader to downsample the [RenderTexture].
 */
class CustomMipmapGenerator(
    private val vertexShader: String = "/pulseengine/shaders/mipmap/mip_downsample.vert",
    private val fragmentShader: String = "/pulseengine/shaders/mipmap/mip_downsample.frag"
) : MipmapGenerator() {

    private lateinit var program: ShaderProgram
    private lateinit var renderer: FullFrameRenderer
    private lateinit var fbo: FrameBufferObject

    override fun onInit(engine: PulseEngineInternal)
    {
        if (!this::program.isInitialized)
        {
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader(vertexShader)),
                engine.asset.loadNow(FragmentShader(fragmentShader))
            )
            renderer = FullFrameRenderer(program)
            fbo = FrameBufferObject.create(0, 0, emptyList()) // No textures, we attach the incoming render texture later
        }

        renderer.init()
    }

    override fun onGenerate(engine: PulseEngineInternal, texture: RenderTexture)
    {
        fbo.bind()
        program.bind()
        program.setUniformSampler("tex", texture)

        for (level in 1 until getLevelCount(texture.width, texture.height))
        {
            val prevLevel  = level - 1
            val prevWidth  = max(1, texture.width shr prevLevel)
            val prevHeight = max(1, texture.height shr prevLevel)
            val currWidth  = max(1, texture.width shr level)
            val currHeight = max(1, texture.height shr level)

            glViewport(0, 0, currWidth, currHeight)
            fbo.attachOutputTexture(texture, mipLevel = level)
            program.setUniform("prevMipLevel", prevLevel)
            program.setUniform("prevTexSize", prevWidth, prevHeight)
            renderer.draw()
        }

        fbo.release()
    }

    override fun onDestroy()
    {
        program.destroy()
        renderer.destroy()
        fbo.destroy()
    }
}