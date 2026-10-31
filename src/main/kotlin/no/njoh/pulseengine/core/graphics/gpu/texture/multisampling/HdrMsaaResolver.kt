package no.njoh.pulseengine.core.graphics.gpu.texture.multisampling

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.FullscreenPass
import no.njoh.pulseengine.core.graphics.gpu.buffer.FrameBufferObject
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA32F
import org.lwjgl.opengl.GL33.*

/** 
 * Brightness-weighted MSAA resolve. Retains HDR but deliberately reduces bright edge energy. 
 * @property exposureScale Linear exposure used for weighting. Zero selects the hardware resolve.
 */
class HdrMsaaResolver(var exposureScale: Float = 0f) : MsaaResolver() 
{
    private lateinit var program: ShaderProgram
    private lateinit var fullscreenPass: FullscreenPass

    override val enabled get() = exposureScale > 0f

    override fun supportsFormat(format: TextureFormat) = format == RGBA16F || format == RGBA32F

    override fun onInit(engine: PulseEngineInternal)
    {
        if (!this::program.isInitialized)
        {
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/utils/ms_hdr_resolve.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/utils/ms_hdr_resolve.frag"))
            )
            fullscreenPass = FullscreenPass(program)
        }

        fullscreenPass.init()
    }

    override fun onResolve(sourceTex: RenderTexture, destinationFbo: FrameBufferObject)
    {
        val outputTex = requireNotNull(destinationFbo.getTextureOrNull(sourceTex.attachmentPoint)) { "Missing resolve destination FBO for ${sourceTex.attachmentPoint}" }
        
        require(supportsFormat(outputTex.format)) { "HDR resolve requires RGBA16F or RGBA32F color" }
        require(sourceTex.sampleCount > 1 && sourceTex.width == outputTex.width && sourceTex.height == outputTex.height) { "HDR resolve requires matching multisample dimensions" }
        require(sourceTex.multisampling != NONE && outputTex.multisampling == NONE) { "HDR resolve requires a multisample source and single-sample destination" }

        destinationFbo.bind()
        destinationFbo.setDrawBuffer(sourceTex.attachmentPoint)

        glViewport(0, 0, outputTex.width, outputTex.height)
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_STENCIL_TEST)
        glDisable(GL_CULL_FACE)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_RASTERIZER_DISCARD)
        glDisable(GL_COLOR_LOGIC_OP)
        glDisablei(GL_BLEND, 0)
        glColorMaski(0, true, true, true, true)

        program.bind()
        program.setUniformSampler("uColorMS", sourceTex)
        program.setUniform("uSamples", sourceTex.sampleCount)
        program.setUniform("uExposure", exposureScale)
        fullscreenPass.draw()
        program.unbind()
    }

    override fun onDestroy()
    {
        fullscreenPass.destroy()
        program.destroy()
    }
}