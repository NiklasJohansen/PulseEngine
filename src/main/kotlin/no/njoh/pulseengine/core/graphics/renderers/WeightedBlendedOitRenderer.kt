package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.api.TextureFormat.R16F
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.api.objects.FrameBufferObject
import no.njoh.pulseengine.core.graphics.api.world.PreparedRenderPass
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawWorldRenderBucket
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.defineShaderVariant
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
    private lateinit var compositeRenderer: FullFrameRenderer
    private lateinit var fbo: FrameBufferObject

    private val oitTextureDescriptors = listOf(
        TextureDescriptor(format = RGBA16F, filter = NEAREST, attachment = Attachment.COLOR_TEXTURE_0),
        TextureDescriptor(format = R16F, filter = NEAREST, attachment = Attachment.COLOR_TEXTURE_1)
    )

    fun init(engine: PulseEngineInternal, surface: Surface)
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

            compositeProgram = ShaderProgram.create(compositeVertex, compositeFragment)
            accumPrograms = ShaderProgramSet(accumStaticProgram, accumSkinnedProgram)
            revealagePrograms = ShaderProgramSet(revealageStaticProgram, revealageSkinnedProgram)
            fbo = FrameBufferObject.create(surface.config.width, surface.config.height, oitTextureDescriptors)
            compositeRenderer = FullFrameRenderer(compositeProgram)
        }

        compositeRenderer.init()
    }

    fun render(
        engine: PulseEngineInternal,
        surface: SurfaceInternal,
        bucket: WorldRenderBucket,
        preparedPass: PreparedRenderPass,
        configureAccumProgram: (ShaderProgram, PulseEngineInternal, Surface) -> Unit
    ) {
        if (bucket.size == 0) return

        updateFbo(surface)
        surface.renderTarget.resolveDepth(engine)

        val opaqueDepthTex = surface.renderTarget.getTextures().firstOrNull { it.attachment == Attachment.DEPTH_TEXTURE }

        accumulate(engine, surface, bucket, preparedPass, opaqueDepthTex, configureAccumProgram)
        composite(surface)
    }

    private fun accumulate(
        engine: PulseEngineInternal,
        surface: Surface,
        bucket: WorldRenderBucket,
        preparedPass: PreparedRenderPass,
        opaqueDepthTex: RenderTexture?,
        configureAccumProgram: (ShaderProgram, PulseEngineInternal, Surface) -> Unit
    ) = measure({ "wboit accumulate (" plus bucket.totalInstanceCount() plus "i, " plus bucket.size plus "b)" }) {

        configureAccumProgram(accumPrograms.staticProgram, engine, surface)
        configureAccumProgram(accumPrograms.skinnedProgram, engine, surface)
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
        drawWorldRenderBucket(bucket, preparedPass, accumPrograms)

        fbo.attachOutputTexture(fbo.getTexture(1))
        glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_COLOR)
        drawWorldRenderBucket(bucket, preparedPass, revealagePrograms)

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
        compositeRenderer.draw()
    }

    private fun configureWboitProgram(program: ShaderProgram, opaqueDepthTex: RenderTexture?, alphaCutoff: Float) 
    {
        program.bind()
        program.setUniform("uUseOpaqueDepthTex", opaqueDepthTex != null)
        program.setUniform("uOpaqueDepthTexSize", opaqueDepthTex?.width?.toFloat() ?: 1f, opaqueDepthTex?.height?.toFloat() ?: 1f)
        program.setUniform("uWboitAlphaCutoff", alphaCutoff)

        if (opaqueDepthTex != null)
            program.setUniformSampler("uOpaqueDepthTex", opaqueDepthTex, filter = NEAREST)
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
            compositeRenderer.destroy()
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