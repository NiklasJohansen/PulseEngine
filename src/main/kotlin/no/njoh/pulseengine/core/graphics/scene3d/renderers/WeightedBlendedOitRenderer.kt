package no.njoh.pulseengine.core.graphics.scene3d.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureDescriptor
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.R16F
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.gpu.buffer.FrameBufferObject
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawPayload
import no.njoh.pulseengine.core.graphics.scene3d.draw.RenderBucket
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawRenderBucket
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.gpu.shader.defineShaderVariant
import no.njoh.pulseengine.core.graphics.gpu.FullscreenPass
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_COLOR
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_COLOR
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_ZERO
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glDepthMask
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL14.GL_FUNC_ADD
import org.lwjgl.opengl.GL14.glBlendEquation
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import org.lwjgl.opengl.GL30.glClearBufferfv

class WeightedBlendedOitRenderer
{
    var alphaCutoff = 0.04f

    private lateinit var accumPrograms: ShaderProgramSet
    private lateinit var revealagePrograms: ShaderProgramSet
    private lateinit var compositeProgram: ShaderProgram
    private lateinit var compositePass: FullscreenPass
    private lateinit var fbo: FrameBufferObject

    private val oitTextureDescriptors = listOf(
        TextureDescriptor(format = RGBA16F, filter = NEAREST, attachment = Attachment.COLOR_TEXTURE_0),
        TextureDescriptor(format = R16F, filter = NEAREST, attachment = Attachment.COLOR_TEXTURE_1)
    )

    fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        if (!this::compositeProgram.isInitialized)
        {
            val compositeVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/surface.vert"))
            val compositeFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/wboit_composite.frag"))
            val revealageFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_wboit_revealage.frag"))
            val staticVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr.vert", ::transformModelVertexShader))
            val skinnedVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr_skinned.vert", ::transformModelVertexShader))
            val accumFragment = engine.asset.loadNow(FragmentShader(
                name = "/pulseengine/shaders/renderers/model_pbr.frag#wboit_accum",
                filePath = "/pulseengine/shaders/renderers/model_pbr.frag",
                transform = defineShaderVariant("PBR_OUTPUT_WBOIT_ACCUM")
            ))

            val accumStaticProgram = ShaderProgram.create(staticVertex, accumFragment)
            val accumSkinnedProgram = ShaderProgram.create(skinnedVertex, accumFragment)
            val revealageStaticProgram = ShaderProgram.create(staticVertex, revealageFragment)
            val revealageSkinnedProgram = ShaderProgram.create(skinnedVertex, revealageFragment)

            accumPrograms = ShaderProgramSet(accumStaticProgram, accumSkinnedProgram)
            revealagePrograms = ShaderProgramSet(revealageStaticProgram, revealageSkinnedProgram)
            compositeProgram = ShaderProgram.create(compositeVertex, compositeFragment)
            compositePass = FullscreenPass(compositeProgram)
        }
        else fbo.destroy()

        fbo = FrameBufferObject.create(surface.config.width, surface.config.height, oitTextureDescriptors)
        compositePass.init()
    }

    fun render(
        engine: PulseEngineInternal,
        surface: SurfaceInternal,
        bucket: RenderBucket,
        drawPayload: DrawPayload,
        configureAccumProgram: (ShaderProgram) -> Unit
    ) {
        if (bucket.size == 0) return

        updateFbo(surface)
        surface.renderTarget.resolveDepth(engine)

        val opaqueDepthTex = surface.renderTarget.getTextures().firstOrNull { it.attachment == DEPTH_TEXTURE }

        accumulate(engine, surface, bucket, drawPayload, opaqueDepthTex, configureAccumProgram)
        composite(surface)
    }

    private fun accumulate(
        engine: PulseEngineInternal,
        surface: Surface,
        bucket: RenderBucket,
        drawPayload: DrawPayload,
        opaqueDepthTex: RenderTexture?,
        configureAccumProgram: (ShaderProgram) -> Unit
    ) = measure("wboit_accumulate", label = { "Wboit accumulate (" plus bucket.instanceCount plus "i, " plus bucket.size plus "b)" }) {

        configureAccumProgram(accumPrograms.staticProgram)
        configureAccumProgram(accumPrograms.skinnedProgram)
        configureWboitProgram(accumPrograms.staticProgram, opaqueDepthTex, alphaCutoff)
        configureWboitProgram(accumPrograms.skinnedProgram, opaqueDepthTex, alphaCutoff)
        configureRevealageProgram(revealagePrograms.staticProgram, engine, surface, opaqueDepthTex, alphaCutoff)
        configureRevealageProgram(revealagePrograms.skinnedProgram, engine, surface, opaqueDepthTex, alphaCutoff)

        fbo.bind()
        glViewport(0, 0, surface.config.width, surface.config.height)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glEnable(GL_BLEND)
        glBlendEquation(GL_FUNC_ADD)
        glClearBufferfv(GL_COLOR, 0, ZERO_ARRAY)
        glClearBufferfv(GL_COLOR, 1, ONE_ARRAY)

        fbo.attachOutputTexture(fbo.getTexture(0))
        glBlendFunc(GL_ONE, GL_ONE)
        drawRenderBucket(bucket, drawPayload, accumPrograms)

        fbo.attachOutputTexture(fbo.getTexture(1))
        glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_COLOR)
        drawRenderBucket(bucket, drawPayload, revealagePrograms)

        fbo.release()
    }

    private fun composite(surface: SurfaceInternal) = measure("wboit composite")
    {
        val accumTex = fbo.getTexture(0)
        val revealageTex = fbo.getTexture(1)

        surface.renderTarget.begin()
        glViewport(0, 0, surface.config.width, surface.config.height)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glEnable(GL_BLEND)
        glBlendEquation(GL_FUNC_ADD)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

        compositeProgram.bind()
        compositeProgram.setUniformSampler("uAccumTex", accumTex, filter = NEAREST)
        compositeProgram.setUniformSampler("uRevealageTex", revealageTex, filter = NEAREST)
        compositePass.draw()
    }

    private fun configureWboitProgram(program: ShaderProgram, opaqueDepthTex: RenderTexture?, alphaCutoff: Float) 
    {
        program.bind()
        program.setUniform("uUseOpaqueDepthTex", opaqueDepthTex != null)
        program.setUniform("uOpaqueDepthTexSize", opaqueDepthTex?.width?.toFloat() ?: 1f, opaqueDepthTex?.height?.toFloat() ?: 1f)
        program.setUniform("uWboitAlphaCutoff", alphaCutoff)

        if (opaqueDepthTex != null)
            program.setUniformSampler("uOpaqueDepthTex", opaqueDepthTex, filter = NEAREST)
        else
            program.assignSamplerUnit("uOpaqueDepthTex")
    }

    private fun configureRevealageProgram(program: ShaderProgram, engine: PulseEngineInternal, surface: Surface, opaqueDepthTex: RenderTexture?, alphaCutoff: Float)
    {
        program.bind()
        program.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        program.setUniform("uViewProjection", surface.camera.viewProjectionMatrix)
        configureWboitProgram(program, opaqueDepthTex, alphaCutoff)
    }

    private fun updateFbo(surface: Surface)
    {
        if (!fbo.matches(surface.config.width, surface.config.height, oitTextureDescriptors))
        {
            fbo.destroy()
            fbo = FrameBufferObject.create(surface.config.width, surface.config.height, oitTextureDescriptors)
        }
    }

    fun destroy()
    {
        if (this::compositeProgram.isInitialized)
        {
            accumPrograms.staticProgram.destroy()
            accumPrograms.skinnedProgram.destroy()
            revealagePrograms.staticProgram.destroy()
            revealagePrograms.skinnedProgram.destroy()
            compositePass.destroy()
            compositeProgram.destroy()
            fbo.destroy()
        }
    }
    
    companion object
    {
        val ZERO_ARRAY = floatArrayOf(0f, 0f, 0f, 0f)
        val ONE_ARRAY = floatArrayOf(1f, 1f, 1f, 1f)
    }
}