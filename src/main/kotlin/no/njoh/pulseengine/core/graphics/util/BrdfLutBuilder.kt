package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.FrameBufferObject
import no.njoh.pulseengine.core.graphics.renderers.FullFrameRenderer
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glViewport

object BrdfLutBuilder
{
    fun generate(engine: PulseEngineInternal, dstTex: Texture)
    {
        val program = ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/utils/brdf_lut.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/utils/brdf_lut.frag"))
        )

        val texArray = engine.gfx.textureBank.getTextureArray(dstTex) ?: error("No texture array found for dstTex (${dstTex.name})")
        val renderer = FullFrameRenderer(program).also { it.init() }
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

        renderer.draw()

        fbo.release()
        program.destroy()
    }
}