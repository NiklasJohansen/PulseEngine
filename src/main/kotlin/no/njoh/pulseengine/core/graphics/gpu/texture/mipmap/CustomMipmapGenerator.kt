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

/**
 * A custom mipmap generator that uses a shader to downsample the [RenderTexture].
 */
class CustomMipmapGenerator(
    private val vertexShader: String = "/pulseengine/shaders/mipmap/mip_downsample.vert",
    private val fragmentShader: String = "/pulseengine/shaders/mipmap/mip_downsample.frag"
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

        for (level in 1 until getLevelCount(texture.width, texture.height))
        {
            val prevLevel  = level - 1
            val prevWidth  = max(1, texture.width  shr prevLevel)
            val prevHeight = max(1, texture.height shr prevLevel)
            val currWidth  = max(1, texture.width  shr level)
            val currHeight = max(1, texture.height shr level)

            glViewport(0, 0, currWidth, currHeight)
            fbo.attachOutputTexture(texture, mipLevel = level)
            program.setUniform("prevMipLevel", prevLevel)
            program.setUniform("prevTexSize", prevWidth, prevHeight)
            fullscreenPass.draw()
        }

        fbo.release()
        glViewport(0, 0, texture.width, texture.height)
    }

    override fun onDestroy()
    {
        program.destroy()
        fullscreenPass.destroy()
        fbo.destroy()
    }
}