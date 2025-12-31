package no.njoh.pulseengine.core.graphics.api.mipmap

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.FrameBufferObject
import no.njoh.pulseengine.core.graphics.renderers.FullFrameRenderer
import org.lwjgl.opengl.GL11.*
import kotlin.math.max

class DepthPyramidGenerator(
    private val vertexShader: String = "/pulseengine/shaders/mipmap/mip_downsample.vert",
    private val fragmentShader: String = "/pulseengine/shaders/mipmap/mip_depth_pyramid.frag"
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

        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_BLEND)
        glDisable(GL_CULL_FACE)
        glColorMask(false, false, false, false)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_ALWAYS)
        glDepthMask(true)

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
        
        // Restore
        glViewport(0, 0, texture.width, texture.height)
        glDepthFunc(GL_LEQUAL)
        glColorMask(true, true, true, true)
    }

    override fun onDestroy()
    {
        program.destroy()
        renderer.destroy()
        fbo.destroy()
    }
}