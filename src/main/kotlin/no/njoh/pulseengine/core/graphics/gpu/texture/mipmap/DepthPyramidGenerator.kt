package no.njoh.pulseengine.core.graphics.gpu.texture.mipmap

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.buffer.FrameBufferObject
import no.njoh.pulseengine.core.graphics.gpu.FullscreenPass
import org.lwjgl.opengl.GL11.*
import kotlin.math.max

class DepthPyramidGenerator(
    private val vertexShader: String = "/pulseengine/shaders/mipmap/mip_downsample.vert",
    private val fragmentShader: String = "/pulseengine/shaders/mipmap/mip_depth_pyramid.frag"
) : MipmapGenerator() {

    private lateinit var program: ShaderProgram
    private lateinit var fullscreenPass: FullscreenPass
    private lateinit var fbo: FrameBufferObject

    override fun onInit(engine: PulseEngineInternal)
    {
        if (!this::program.isInitialized)
        {
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader(vertexShader)),
                engine.asset.loadNow(FragmentShader(fragmentShader))
            )
            fullscreenPass = FullscreenPass(program)
            fbo = FrameBufferObject.create(0, 0, emptyList()) // No textures, we attach the incoming render texture later
        }

        fullscreenPass.init()
    }

    override fun onGenerate(engine: PulseEngineInternal, texture: RenderTexture)
    {
        fbo.bind()
        program.bind()
        program.setUniformSampler("tex", texture)

        glDisable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_ALWAYS)
        glDepthMask(true)
        glColorMask(false, false, false, false)

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
            fullscreenPass.draw()
        }

        fbo.release()
        
        // Restore
        glViewport(0, 0, texture.width, texture.height)
        glDepthFunc(GL_LEQUAL)
        glColorMask(true, true, true, true)
        glEnable(GL_BLEND)
    }

    override fun onDestroy()
    {
        program.destroy()
        fullscreenPass.destroy()
        fbo.destroy()
    }
}