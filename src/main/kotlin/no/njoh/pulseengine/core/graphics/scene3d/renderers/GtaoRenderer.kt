package no.njoh.pulseengine.core.graphics.scene3d.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment.*
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.*
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureDescriptor
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.*
import no.njoh.pulseengine.core.graphics.gpu.shader.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.gpu.buffer.FrameBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.StaticBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleVertices
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glViewport

class GtaoRenderer(
    override val order: Int              = 30,
    var intensity: Float                 = 1.5f,
    var slices: Int                      = 3,
    var maxSteps: Int                    = 8,
    var maxRadiusPixels: Float           = 600f,
    var radiusMeters: Float              = 1.5f,
    var thicknessMeters: Float           = 0.01f,
    var denoisePasses: Int               = 1,
    var denoiseRadius: Int               = 5,
    var denoiseBlurWidth: Float          = 3f,
    var denoiseEdgePreservation: Float   = 0.9f,
    var downsampleFactor: Int            = 2,
    var upsampleBlur: Float              = 0.3f,
    var upsampleEdgeTolerance: Float     = 1f,
    var temporalAccumulation: Float      = 0.95f,
    var temporalHistoryRejection: Float  = 0.5f
) : Renderer() {

    private lateinit var aoProgram: ShaderProgram
    private lateinit var denoiseProgram: ShaderProgram
    private lateinit var upsampleProgram: ShaderProgram
    private lateinit var temporalProgram: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject
    private lateinit var aoFbo: FrameBufferObject
    private lateinit var denoiseFbo: FrameBufferObject
    private lateinit var upsampleFbo: FrameBufferObject
    private lateinit var temporalFbo: FrameBufferObject
    private lateinit var prevDepthFbo: FrameBufferObject

    private var outputAoTex: RenderTexture? = null
    private var prevInvProjection = Matrix4f()
    private var prevViewProjection = Matrix4f()
    private var historyValid = false

    private var aoTextureDescriptors = 
        listOf(TextureDescriptor(format = R16F, filter = NEAREST, multisampling = NONE, scale = 1f / downsampleFactor))

    private var denoiseTextureDescriptors =
        (0..1).map { (TextureDescriptor(format = R16F, filter = NEAREST, multisampling = NONE, scale = 1f / downsampleFactor)) }

    private var upsampleTextureDescriptors = 
        listOf(TextureDescriptor(format = R16F, filter = NEAREST, multisampling = NONE))

    private var temporalTextureDescriptors = 
        (0..1).map { TextureDescriptor(format = R16F, filter = LINEAR) } // 2 ping-pong textures for temporal acc

    private var prevDepthTextureDescriptors = 
        listOf(TextureDescriptor(filter = NEAREST, attachment = DEPTH_TEXTURE))

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        if (!this::aoProgram.isInitialized)
        {
            vbo = StaticBufferObject.createFullscreenUvTriangleArrayBuffer()
            aoProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/gtao.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/gtao.frag"))
            )
            denoiseProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/gtao.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/gtao_denoise.frag"))
            )
            upsampleProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/gtao.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/gtao_upsample.frag"))
            )
            temporalProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/gtao.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/gtao_temporal.frag"))
            )
        }
        else
        {
            vao.destroy()
            aoFbo.destroy()
            denoiseFbo.destroy()
            upsampleFbo.destroy()
            temporalFbo.destroy()
            prevDepthFbo.destroy()
        }

        aoFbo = FrameBufferObject.create(surface.config.width, surface.config.height, aoTextureDescriptors)
        denoiseFbo = FrameBufferObject.create(surface.config.width, surface.config.height, denoiseTextureDescriptors)
        upsampleFbo = FrameBufferObject.create(surface.config.width, surface.config.height, upsampleTextureDescriptors)
        temporalFbo = FrameBufferObject.create(surface.config.width, surface.config.height, temporalTextureDescriptors)
        prevDepthFbo = FrameBufferObject.create(surface.config.width, surface.config.height, prevDepthTextureDescriptors)
        outputAoTex = null
        historyValid = false
        prevInvProjection.identity()
        prevViewProjection.identity()

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        aoProgram.bind()
        VertexAttributeLayout()
            .withAttribute("position", 2, GL_FLOAT)
            .withAttribute("texCoord", 2, GL_FLOAT)
            .bind(aoProgram)
        vao.release()
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        increaseBatchSize() // To ensure the batch is rendered
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        // TODO: The resolved single sampled depth textures has jagged edges. Make custom MSAA resolver that writes coverage to a separate channel
        // TODO: See latest reply here: https://chatgpt.com/share/697694a7-2ce0-8007-89e9-49ac8be5095d
        val depthTex = surface.renderTarget.getTextures().firstOrNull { it.attachment == DEPTH_TEXTURE } ?: return

        // Main GTAO render pass
        var aoTex = renderGtao(surface, depthTex)

        // Denoising
        if (denoiseRadius > 0 && denoisePasses > 0)
            aoTex = denoise(engine, surface, aoTex, depthTex)

        // Upsampling
        if (downsampleFactor > 1)
            aoTex = upsample(surface, aoTex, depthTex)

        // Temporal accumulation
        if (temporalAccumulation > 0.0f)
        {
            aoTex = temporallyAccumulate(engine, surface, aoTex, depthTex)
            storeDepth(surface)
            historyValid = true
        }
        else historyValid = false

        outputAoTex = aoTex
        
        // Rebind surface render target for further rendering
        surface.renderTarget.begin()
        glViewport(0, 0, surface.config.width, surface.config.height)
    }

    private fun renderGtao(surface: SurfaceInternal, depthTex: RenderTexture): RenderTexture = measure("Gtao render")
    {
        aoTextureDescriptors[0].scale = 1f / downsampleFactor
        updateFbo(aoFbo, aoTextureDescriptors, surface, onNewFbo = { aoFbo = it; historyValid = false })

        val aoTex = aoFbo.getTexture()
        val maxMipIndex = (depthTex.mipmapGenerator?.getLevelCount(depthTex.width, depthTex.height) ?: 1) - 1

        glDisable(GL_DEPTH_TEST)
        glDisable(GL_BLEND)
        glViewport(0, 0, aoTex.width, aoTex.height)

        aoFbo.bind()
        aoFbo.clear()

        aoProgram.bind()
        aoProgram.setUniformSampler("uDepthTex", depthTex, filter = NEAREST_MIPMAP)
        aoProgram.setUniform("uDepthMaxMipIndex", maxMipIndex)
        aoProgram.setUniform("uDepthTexSize", depthTex.width, depthTex.height)
        aoProgram.setUniform("uAoTexSize", aoTex.width, aoTex.height)
        aoProgram.setUniform("uInvView", surface.camera.invViewMatrix)
        aoProgram.setUniform("uInvProj", surface.camera.invProjectionMatrix)
        aoProgram.setUniform("uProj", surface.camera.projectionMatrix)
        aoProgram.setUniform("uRadiusMeters", radiusMeters)
        aoProgram.setUniform("uMaxRadiusPixels", maxRadiusPixels)
        aoProgram.setUniform("uThicknessMeters", thicknessMeters)
        aoProgram.setUniform("uNumSlices", slices)
        aoProgram.setUniform("uMaxSteps", maxSteps)
        aoProgram.setUniform("uDownsampleFactor", downsampleFactor)

        drawTriangleVertices(vao, 0, 3)

        aoFbo.release()
        
        return aoTex
    }

    private fun denoise(
        engine: PulseEngineInternal, 
        surface: SurfaceInternal, 
        aoTex: RenderTexture, 
        depthTex: RenderTexture
    ): RenderTexture = measure("Gtao denoise") {

        denoiseTextureDescriptors.forEachFast { it.scale = 1f / downsampleFactor }
        updateFbo(denoiseFbo, denoiseTextureDescriptors, surface, onNewFbo = { denoiseFbo = it; historyValid = false })

        val i = engine.data.frameNumber.toInt() % 2
        val aoTexA = denoiseFbo.getTextureOrNull(i) ?: return aoTex
        val aoTexB = denoiseFbo.getTextureOrNull((i + 1) % 2) ?: return aoTex
        var aoTexIn = aoTex
        
        glViewport(0, 0, aoTexA.width, aoTexA.height)

        denoiseFbo.bind()
        denoiseFbo.clear()
        
        denoiseProgram.bind()
        denoiseProgram.setUniformSampler("uDepthTex", depthTex, filter = NEAREST_MIPMAP)
        denoiseProgram.setUniform("uProj", surface.camera.projectionMatrix)
        denoiseProgram.setUniform("uDepthTexSize", depthTex.width, depthTex.height)
        denoiseProgram.setUniform("uAoTexSize", aoTex.width, aoTex.height)
        denoiseProgram.setUniform("uDownsampleFactor", downsampleFactor)
        denoiseProgram.setUniform("uPixelRadius", denoiseRadius)
        denoiseProgram.setUniform("uBlurWidth", denoiseBlurWidth)
        denoiseProgram.setUniform("uAoEdgePreservation", denoiseEdgePreservation)

        repeat(denoisePasses)
        {
            // Horizontal pass
            denoiseProgram.setUniformSampler("uAoTex", aoTexIn, filter = NEAREST)
            denoiseProgram.setUniform("uDir", 1, 0)
            denoiseFbo.attachOutputTexture(aoTexA)
            drawTriangleVertices(vao, 0, 3)

            // Vertical pass
            denoiseProgram.setUniformSampler("uAoTex", aoTexA, filter = NEAREST)
            denoiseProgram.setUniform("uDir", 0, 1)
            denoiseFbo.attachOutputTexture(aoTexB)
            drawTriangleVertices(vao, 0, 3)

            aoTexIn = aoTexB
        }

        denoiseFbo.release()

        return aoTexB       
    }
    
    private fun upsample(
        surface: SurfaceInternal, 
        aoTex: RenderTexture, 
        depthTex: RenderTexture
    ): RenderTexture = measure("Gtao upsample") {

        updateFbo(upsampleFbo, upsampleTextureDescriptors, surface, onNewFbo = { upsampleFbo = it; historyValid = false })

        val upsampledAoTex = upsampleFbo.getTextureOrNull(0) ?: return aoTex

        glViewport(0, 0, upsampledAoTex.width, upsampledAoTex.height)

        upsampleFbo.bind()
        upsampleFbo.clear()

        upsampleProgram.bind()
        upsampleProgram.setUniformSampler("uAoTex", aoTex, filter = NEAREST)
        upsampleProgram.setUniformSampler("uDepthTex", depthTex, filter = NEAREST_MIPMAP)
        upsampleProgram.setUniform("uResolution", upsampledAoTex.width, upsampledAoTex.height)
        upsampleProgram.setUniform("uAoTexSize", aoTex.width, aoTex.height)
        upsampleProgram.setUniform("uInvProj", surface.camera.invProjectionMatrix)
        upsampleProgram.setUniform("uDownsampleFactor", downsampleFactor)
        upsampleProgram.setUniform("uEdgeTolerance", upsampleEdgeTolerance)
        upsampleProgram.setUniform("uUpsampleBlur", upsampleBlur)

        drawTriangleVertices(vao, 0, 3)

        upsampleFbo.release()

        return upsampledAoTex
    }

    private fun temporallyAccumulate(
        engine: PulseEngineInternal,
        surface: SurfaceInternal, 
        aoTex: RenderTexture, 
        depthTex: RenderTexture
    ): RenderTexture = measure("Gtao temporal accumulate") {

        updateFbo(temporalFbo, temporalTextureDescriptors, surface, onNewFbo = { temporalFbo = it; historyValid = false })

        val fn = engine.data.frameNumber.toInt()
        val historyAoTex = temporalFbo.getTexture(fn % 2)
        val outputAoTex = temporalFbo.getTexture((fn + 1) % 2)
        val prevDeptTex = prevDepthFbo.getTextureOrNull(0) ?: return aoTex

        glViewport(0, 0, outputAoTex.width, outputAoTex.height)

        temporalFbo.bind()
        temporalFbo.attachOutputTexture(outputAoTex)
        temporalFbo.clear()

        temporalProgram.bind()
        temporalProgram.setUniformSampler("uAoCurrent", aoTex, filter = LINEAR)
        temporalProgram.setUniformSampler("uAoHistory", historyAoTex, filter = LINEAR)
        temporalProgram.setUniformSampler("uDepthCurrent", depthTex, filter = NEAREST)
        temporalProgram.setUniformSampler("uDepthPrev", prevDeptTex, filter = NEAREST)
        temporalProgram.setUniform("uResolution", outputAoTex.width, outputAoTex.height)
        temporalProgram.setUniform("uInvProj", surface.camera.invProjectionMatrix)
        temporalProgram.setUniform("uInvView", surface.camera.invViewMatrix)
        temporalProgram.setUniform("uPrevInvProj", prevInvProjection)
        temporalProgram.setUniform("uPrevViewProj", prevViewProjection)
        temporalProgram.setUniform("uHistoryValid", historyValid)
        temporalProgram.setUniform("uTemporalFeedback", if (historyValid) temporalAccumulation else 0f)
        temporalProgram.setUniform("uHistoryClampStrength", temporalHistoryRejection)

        drawTriangleVertices(vao, 0, 3)

        temporalFbo.release()

        prevViewProjection.set(surface.camera.viewProjectionMatrix)
        prevInvProjection.set(surface.camera.invProjectionMatrix)

        return outputAoTex
    }

    private fun storeDepth(surface: SurfaceInternal) = measure("Gtao store depth")
    {
        updateFbo(prevDepthFbo, prevDepthTextureDescriptors, surface, onNewFbo = { prevDepthFbo = it; historyValid = false })

        surface.renderTarget.getFbo().resolveDepthToFBO(prevDepthFbo)
    }

    private inline fun updateFbo(fbo: FrameBufferObject, texDescriptors: List<TextureDescriptor>, surface: Surface, onNewFbo: (FrameBufferObject) -> Unit)
    {
        if (fbo.matches(surface.config.width, surface.config.height, texDescriptors))
            return // No need to update
        fbo.destroy()
        onNewFbo(FrameBufferObject.create(surface.config.width, surface.config.height, texDescriptors))
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        vbo.destroy()
        vao.destroy()
        aoProgram.destroy()
        denoiseProgram.destroy()
        upsampleProgram.destroy()
        temporalProgram.destroy()
        aoFbo.destroy()
        denoiseFbo.destroy()
        upsampleFbo.destroy()
        temporalFbo.destroy()
        prevDepthFbo.destroy()
        outputAoTex = null
        historyValid = false
    }

    fun getAoRenderTexture() = outputAoTex
}