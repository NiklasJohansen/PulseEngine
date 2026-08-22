package no.njoh.pulseengine.core.graphics.scene3d.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_1
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_2
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureDescriptor
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.R16F
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.R32UI
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
import org.lwjgl.opengl.GL30.glClearBufferuiv
import org.lwjgl.opengl.GL30.glDisablei
import org.lwjgl.opengl.GL30.glEnablei

class WeightedBlendedOitRenderer
{
    var alphaCutoff = 0.04f

    private lateinit var accumPrograms: ShaderProgramSet
    private lateinit var renderIdViewAccumPrograms: ShaderProgramSet
    private lateinit var revealagePrograms: ShaderProgramSet
    private lateinit var renderIdRevealagePrograms: ShaderProgramSet
    private lateinit var compositeProgram: ShaderProgram
    private lateinit var renderIdCompositeProgram: ShaderProgram
    private lateinit var compositePass: FullscreenPass
    private lateinit var renderIdCompositePass: FullscreenPass
    private lateinit var fbo: FrameBufferObject

    private val baseTextureDescriptors = listOf(
        TextureDescriptor(format = RGBA16F, filter = NEAREST, attachmentPoint = COLOR_TEXTURE_0),
        TextureDescriptor(format = R16F, filter = NEAREST, attachmentPoint = COLOR_TEXTURE_1)
    )
    private val renderIdTextureDescriptors = baseTextureDescriptors +
        TextureDescriptor(format = R32UI, filter = NEAREST, attachmentPoint = COLOR_TEXTURE_2)

    fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        if (!this::compositeProgram.isInitialized)
        {
            val compositeVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/surface.vert"))
            val compositeFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/wboit_composite.frag"))
            val revealageFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_wboit_revealage.frag"))
            val renderIdCompositeFragment = engine.asset.loadNow(FragmentShader(
                name = "/pulseengine/shaders/renderers/wboit_composite.frag#render_id",
                filePath = "/pulseengine/shaders/renderers/wboit_composite.frag",
                transform = defineShaderVariant("WBOIT_OUTPUT_RENDER_ID")
            ))
            val renderIdRevealageFragment = engine.asset.loadNow(FragmentShader(
                name = "/pulseengine/shaders/renderers/model_wboit_revealage.frag#render_id",
                filePath = "/pulseengine/shaders/renderers/model_wboit_revealage.frag",
                transform = defineShaderVariant("WBOIT_OUTPUT_RENDER_ID")
            ))
            val staticVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr.vert", ::transformModelVertexShader))
            val skinnedVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr_skinned.vert", ::transformModelVertexShader))
            val accumFragment = engine.asset.loadNow(FragmentShader(
                name = "/pulseengine/shaders/renderers/model_pbr.frag#wboit_accum",
                filePath = "/pulseengine/shaders/renderers/model_pbr.frag",
                transform = defineShaderVariant("PBR_OUTPUT_WBOIT_ACCUM")
            ))
            val renderIdViewAccumFragment = engine.asset.loadNow(FragmentShader(
                name = "/pulseengine/shaders/renderers/model_pbr.frag#wboit_accum_render_id",
                filePath = "/pulseengine/shaders/renderers/model_pbr.frag",
                transform = defineShaderVariant("PBR_OUTPUT_WBOIT_ACCUM", "PBR_USE_RENDER_ID")
            ))

            val accumStaticProgram = ShaderProgram.create(staticVertex, accumFragment)
            val accumSkinnedProgram = ShaderProgram.create(skinnedVertex, accumFragment)
            val renderIdViewAccumStaticProgram = ShaderProgram.create(staticVertex, renderIdViewAccumFragment)
            val renderIdViewAccumSkinnedProgram = ShaderProgram.create(skinnedVertex, renderIdViewAccumFragment)
            val revealageStaticProgram = ShaderProgram.create(staticVertex, revealageFragment)
            val revealageSkinnedProgram = ShaderProgram.create(skinnedVertex, revealageFragment)
            val renderIdRevealageStaticProgram = ShaderProgram.create(staticVertex, renderIdRevealageFragment)
            val renderIdRevealageSkinnedProgram = ShaderProgram.create(skinnedVertex, renderIdRevealageFragment)

            accumPrograms = ShaderProgramSet(accumStaticProgram, accumSkinnedProgram)
            renderIdViewAccumPrograms = ShaderProgramSet(renderIdViewAccumStaticProgram, renderIdViewAccumSkinnedProgram)
            revealagePrograms = ShaderProgramSet(revealageStaticProgram, revealageSkinnedProgram)
            renderIdRevealagePrograms = ShaderProgramSet(renderIdRevealageStaticProgram, renderIdRevealageSkinnedProgram)
            compositeProgram = ShaderProgram.create(compositeVertex, compositeFragment)
            renderIdCompositeProgram = ShaderProgram.create(compositeVertex, renderIdCompositeFragment)
            compositePass = FullscreenPass(compositeProgram)
            renderIdCompositePass = FullscreenPass(renderIdCompositeProgram)
        }
        else fbo.destroy()

        fbo = FrameBufferObject.create(surface.config.renderWidth, surface.config.renderHeight, baseTextureDescriptors)
        compositePass.init()
        renderIdCompositePass.init()
    }

    fun render(
        engine: PulseEngineInternal,
        surface: SurfaceInternal,
        bucket: RenderBucket,
        drawPayload: DrawPayload,
        writeRenderIds: Boolean,
        visualizeRenderIds: Boolean,
        configureAccumProgram: (ShaderProgram) -> Unit
    ) {
        if (bucket.size == 0) return

        updateFbo(surface, writeRenderIds)
        surface.renderTarget.resolveDepth()

        val opaqueDepthTex = surface.renderTarget.getTexture(DEPTH_TEXTURE)

        accumulate(engine, surface, bucket, drawPayload, opaqueDepthTex, writeRenderIds, visualizeRenderIds, configureAccumProgram)
        composite(surface, writeRenderIds)
    }

    private fun accumulate(
        engine: PulseEngineInternal,
        surface: SurfaceInternal,
        bucket: RenderBucket,
        drawPayload: DrawPayload,
        opaqueDepthTex: RenderTexture?,
        writeRenderIds: Boolean,
        visualizeRenderIds: Boolean,
        configureAccumProgram: (ShaderProgram) -> Unit
    ) = measure("wboit_accumulate", label = { "Wboit accumulate (" plus bucket.instanceCount plus "i, " plus bucket.size plus "b)" }) {

        val activeAccumPrograms = if (visualizeRenderIds) renderIdViewAccumPrograms else accumPrograms
        val activeRevealagePrograms = if (writeRenderIds) renderIdRevealagePrograms else revealagePrograms

        configureAccumProgram(activeAccumPrograms.staticProgram)
        configureAccumProgram(activeAccumPrograms.skinnedProgram)
        configureWboitProgram(activeAccumPrograms.staticProgram, opaqueDepthTex, alphaCutoff)
        configureWboitProgram(activeAccumPrograms.skinnedProgram, opaqueDepthTex, alphaCutoff)
        configureRevealageProgram(activeRevealagePrograms.staticProgram, engine, surface, opaqueDepthTex, alphaCutoff)
        configureRevealageProgram(activeRevealagePrograms.skinnedProgram, engine, surface, opaqueDepthTex, alphaCutoff)

        fbo.bind()
        glViewport(0, 0, surface.config.renderWidth, surface.config.renderHeight)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glEnable(GL_BLEND)
        glBlendEquation(GL_FUNC_ADD)
        glClearBufferfv(GL_COLOR, COLOR_TEXTURE_0.glLocation, ZERO_ARRAY)
        glClearBufferfv(GL_COLOR, COLOR_TEXTURE_1.glLocation, ONE_ARRAY)

        if (writeRenderIds)
            glClearBufferuiv(GL_COLOR, COLOR_TEXTURE_2.glLocation, BACKGROUND_RENDER_ID)

        fbo.attachOutputTexture(fbo.getTexture(0))
        glBlendFunc(GL_ONE, GL_ONE)
        drawRenderBucket(bucket, drawPayload, activeAccumPrograms)

        if (writeRenderIds)
        {
            fbo.setDrawBuffers(COLOR_TEXTURE_1, COLOR_TEXTURE_2)
            glEnablei(GL_BLEND, COLOR_TEXTURE_1.glLocation)
            glDisablei(GL_BLEND, COLOR_TEXTURE_2.glLocation)
        }
        else fbo.attachOutputTexture(fbo.getTexture(1))

        glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_COLOR)
        drawRenderBucket(bucket, drawPayload, activeRevealagePrograms)

        fbo.release()
    }

    private fun composite(surface: SurfaceInternal, writeRenderIds: Boolean) = measure("Wboit composite")
    {
        val accumTex = fbo.getTexture(0)
        val revealageTex = fbo.getTexture(1)
        val activeProgram = if (writeRenderIds) renderIdCompositeProgram else compositeProgram
        val activePass = if (writeRenderIds) renderIdCompositePass else compositePass

        surface.renderTarget.begin()
 
        if (writeRenderIds)
            surface.renderTarget.setDrawBuffers(COLOR_TEXTURE_0, COLOR_TEXTURE_1)
        else 
            surface.renderTarget.setDrawBuffer(COLOR_TEXTURE_0)
        
        glViewport(0, 0, surface.config.renderWidth, surface.config.renderHeight)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glEnable(GL_BLEND)
        glBlendEquation(GL_FUNC_ADD)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

        if (writeRenderIds)
        {
            glEnablei(GL_BLEND, COLOR_TEXTURE_0.glLocation)
            glDisablei(GL_BLEND, COLOR_TEXTURE_1.glLocation)
        }

        activeProgram.bind()
        activeProgram.setUniformSampler("uAccumTex", accumTex, filter = NEAREST)
        activeProgram.setUniformSampler("uRevealageTex", revealageTex, filter = NEAREST)

        if (writeRenderIds)
            activeProgram.setUniformSampler("uRenderIdTex", fbo.getTexture(2), filter = NEAREST)

        activePass.draw()
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

    private fun updateFbo(surface: SurfaceInternal, writeRenderIds: Boolean)
    {
        val descriptors = if (writeRenderIds) renderIdTextureDescriptors else baseTextureDescriptors
        if (!fbo.matches(surface.config.renderWidth, surface.config.renderHeight, descriptors))
        {
            fbo.destroy()
            fbo = FrameBufferObject.create(surface.config.renderWidth, surface.config.renderHeight, descriptors)
        }
    }

    fun destroy()
    {
        if (this::compositeProgram.isInitialized)
        {
            accumPrograms.staticProgram.destroy()
            accumPrograms.skinnedProgram.destroy()
            renderIdViewAccumPrograms.staticProgram.destroy()
            renderIdViewAccumPrograms.skinnedProgram.destroy()
            revealagePrograms.staticProgram.destroy()
            revealagePrograms.skinnedProgram.destroy()
            renderIdRevealagePrograms.staticProgram.destroy()
            renderIdRevealagePrograms.skinnedProgram.destroy()
            compositePass.destroy()
            renderIdCompositePass.destroy()
            compositeProgram.destroy()
            renderIdCompositeProgram.destroy()
            fbo.destroy()
        }
    }
    
    companion object
    {
        val ZERO_ARRAY = floatArrayOf(0f, 0f, 0f, 0f)
        val ONE_ARRAY = floatArrayOf(1f, 1f, 1f, 1f)
        val BACKGROUND_RENDER_ID = intArrayOf(-1, 0, 0, 0)
    }
}