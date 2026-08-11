package no.njoh.pulseengine.core.graphics.scene3d.lighting

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.EnvMap
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.buffer.FrameBufferObject
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint
import no.njoh.pulseengine.core.graphics.gpu.FullscreenPass
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL11.*
import kotlin.math.max

object EnvironmentMapBuilder
{
    fun generateSpecularIBL(engine: PulseEngineInternal, srcEnv: EnvMap, dstEnv: EnvMap, mipCount: Int)
    {
        Logger.info { "Generating specular IBL for ${srcEnv.name} -> ${dstEnv.name}" }

        val dstTextureArray = engine.gfx.textureBank.getTextureArray(dstEnv) ?: error("No texture array found for dstEnv (${dstEnv.name})")
        val srcTextureArray = engine.gfx.textureBank.getTextureArray(srcEnv) ?: error("No texture array found for srcEnv (${srcEnv.name})")
        val program = ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/utils/ibl.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/utils/ibl_specular.frag"))
        )
        val fullscreenPass = FullscreenPass(program).also { it.init() }
        val frameBufferObject = FrameBufferObject.create(1, 1, emptyList())

        try
        {
            program.bind()
            program.setUniformSamplerArray("textureArray", srcTextureArray)
            program.setUniform("srcEnv", srcEnv.handle.textureIndex.toFloat(), srcEnv.uMax, srcEnv.vMax)
            program.setUniform("srcEnvSize", srcEnv.width.toFloat(), srcEnv.height.toFloat())

            val err = glGetError()
            require(err == GL_NO_ERROR) { "GL error after setting srcEnv: $err" }

            frameBufferObject.bind()

            for (mip in 0 until mipCount)
            {
                val mipWidth = max(1, dstEnv.width shr mip)
                val mipHeight = max(1, dstEnv.height shr mip)
                val roughness = mip.toFloat() / (mipCount - 1).coerceAtLeast(1)

                program.setUniform("roughness", roughness)

                frameBufferObject.attachOutputTextureArray(dstTextureArray, dstEnv.handle.textureIndex, AttachmentPoint.COLOR_TEXTURE_0, mip)
                frameBufferObject.checkStatus()

                glViewport(0, 0, mipWidth, mipHeight)
                glClear(GL_COLOR_BUFFER_BIT)

                fullscreenPass.draw()
            }
        }
        finally
        {
            frameBufferObject.release()
            frameBufferObject.destroy()
            fullscreenPass.destroy()
            program.destroy()
        }
    }

    fun generateDiffuseIBL(engine: PulseEngineInternal, srcEnv: EnvMap, dstEnv: EnvMap)
    {
        Logger.info { "Generating diffuse IBL for ${srcEnv.name} -> ${dstEnv.name}" }

        val dstTextureArray = engine.gfx.textureBank.getTextureArray(dstEnv) ?: error("No texture array found for dstEnv (${dstEnv.name})")
        val srcTextureArray = engine.gfx.textureBank.getTextureArray(srcEnv) ?: error("No texture array found for srcEnv (${srcEnv.name})")
        val program = ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/utils/ibl.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/utils/ibl_diffuse.frag"))
        )
        val fullscreenPass = FullscreenPass(program).also { it.init() }
        val frameBufferObject = FrameBufferObject.create(1, 1, emptyList())

        try
        {
            program.bind()
            program.setUniformSamplerArray("textureArray", srcTextureArray)
            program.setUniform("srcEnv", srcEnv.handle.textureIndex.toFloat(), srcEnv.uMax, srcEnv.vMax)

            frameBufferObject.bind()
            frameBufferObject.attachOutputTextureArray(dstTextureArray, index = dstEnv.handle.textureIndex, attachmentPoint = AttachmentPoint.COLOR_TEXTURE_0, mipLevel = 0)
            FrameBufferObject.checkStatus()

            glViewport(0, 0, dstEnv.width, dstEnv.height)
            glClear(GL_COLOR_BUFFER_BIT)

            fullscreenPass.draw()
        }
        finally
        {
            frameBufferObject.release()
            frameBufferObject.destroy()
            fullscreenPass.destroy()
            program.destroy()
        }
    }
}