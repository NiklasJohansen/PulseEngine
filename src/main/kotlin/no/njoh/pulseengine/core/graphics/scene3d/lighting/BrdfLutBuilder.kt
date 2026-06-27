package no.njoh.pulseengine.core.graphics.scene3d.lighting

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.buffer.FrameBufferObject
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment
import no.njoh.pulseengine.core.graphics.gpu.FullscreenPass
import org.lwjgl.opengl.GL11.*

object BrdfLutBuilder
{
    fun generate(engine: PulseEngineInternal, dstTex: Texture)
    {
        val program = ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/utils/brdf_lut.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/utils/brdf_lut.frag"))
        )

        val texArray = engine.gfx.textureBank.getTextureArray(dstTex) ?: error("No texture array found for dstTex (${dstTex.name})")
        val fullscreenPass = FullscreenPass(program).also { it.init() }
        val fbo = FrameBufferObject.createEmpty()

        program.bind()

        fbo.bind()
        fbo.attachOutputTextureArray(
            textureArray = texArray,
            index = dstTex.handle.textureIndex,
            attachment = Attachment.COLOR_TEXTURE_0,
            mipLevel = 0
        )
        fbo.checkStatus()

        glViewport(0, 0, dstTex.width, dstTex.height)
        glDisable(GL_BLEND)
        glDisable(GL_DEPTH_TEST)
        glClear(GL_COLOR_BUFFER_BIT)

        fullscreenPass.draw()

        fbo.release()
        fbo.destroy()
        program.destroy()
        fullscreenPass.destroy()
    }
}