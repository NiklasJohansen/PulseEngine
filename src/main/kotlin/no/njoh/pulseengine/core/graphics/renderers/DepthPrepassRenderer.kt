package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.api.ModelBatchList
import no.njoh.pulseengine.core.graphics.api.ModelProgramSet
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.WorldRenderFrame
import no.njoh.pulseengine.core.graphics.api.WorldRenderState
import no.njoh.pulseengine.core.graphics.api.WorldRenderCameraView
import no.njoh.pulseengine.core.graphics.api.WorldRenderFrameQueue
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawGpuCulledModelBatches
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawModelBatches
import no.njoh.pulseengine.core.graphics.util.GpuModelCuller
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import org.lwjgl.opengl.GL11.*

class DepthPrepassRenderer(
    private val worldRenderState: WorldRenderState,
    override val order: Int = 20
) : Renderer() {

    private lateinit var opaqueStaticProgram: ShaderProgram
    private lateinit var opaqueSkinnedProgram: ShaderProgram
    private lateinit var maskedStaticProgram: ShaderProgram
    private lateinit var maskedSkinnedProgram: ShaderProgram
    private lateinit var opaquePrograms: ModelProgramSet
    private lateinit var maskedPrograms: ModelProgramSet

    private var renderFrameQueue = WorldRenderFrameQueue(worldRenderState)

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::opaqueStaticProgram.isInitialized)
        {
            val staticVertex   = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_depth.vert", ::transformModelVertexShader))
            val skinnedVertex  = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_depth_skinned.vert", ::transformModelVertexShader))
            val opaqueFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_depth_opaque.frag"))
            val maskedFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_depth.frag"))

            opaqueStaticProgram = ShaderProgram.create(staticVertex, opaqueFragment)
            opaqueSkinnedProgram = ShaderProgram.create(skinnedVertex, opaqueFragment)
            maskedStaticProgram = ShaderProgram.create(staticVertex, maskedFragment)
            maskedSkinnedProgram = ShaderProgram.create(skinnedVertex, maskedFragment)

            opaquePrograms = ModelProgramSet(opaqueStaticProgram, opaqueSkinnedProgram)
            maskedPrograms = ModelProgramSet(maskedStaticProgram, maskedSkinnedProgram)
        }
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        renderFrameQueue.initFrame()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        surface.config.hasDepthPrepass = true

        glColorMask(false, false, false, false)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LESS)
        glViewport(0, 0, surface.config.width, surface.config.height)

        renderFrameQueue.forEachReadable()
        {
            configureOpaqueProgram(opaqueStaticProgram, surface)
            configureOpaqueProgram(opaqueSkinnedProgram, surface)
            configureMaskedProgram(maskedStaticProgram, engine, surface)
            configureMaskedProgram(maskedSkinnedProgram, engine, surface)

            worldRenderState.withCameraView(engine, surface.camera, frame = it)
            {
                view -> render(view)
            }
        }

        glColorMask(true, true, true, true)
        glDisable(GL_CULL_FACE)

        surface.renderTarget.resolveDepth(engine)
        surface.getTextures().firstOrNullFast { it.attachment == DEPTH_TEXTURE }?.generateMips(engine)
        surface.renderTarget.begin()
    }

    private fun configureOpaqueProgram(program: ShaderProgram, surface: SurfaceInternal)
    {
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
    }

    private fun configureMaskedProgram(program: ShaderProgram, engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
    }

    private fun render(view: WorldRenderCameraView)
    {
        val opaqueBatches = view.getOpaqueBatches()
        val opaqueCount = opaqueBatches.totalInstanceCount()
        if (opaqueCount > 0)
        {
            measure({"opaque depth (" plus opaqueCount plus "i, " plus opaqueBatches.size plus "b)"})
            {
                drawWorldBatches(opaqueBatches, opaquePrograms, view.getOpaqueCuller(), view)
            }
        }

        val maskedBatches = view.getMaskedBatches()
        val maskedCount = maskedBatches.totalInstanceCount()
        if (maskedCount > 0)
        {
            measure({"masked depth (" plus maskedCount plus "i, " plus maskedBatches.size plus "b)"})
            {
                drawWorldBatches(maskedBatches, maskedPrograms, view.getMaskedCuller(), view)
            }
        }
    }

    private fun drawWorldBatches(batches: ModelBatchList, programs: ModelProgramSet, culler: GpuModelCuller?, view: WorldRenderCameraView)
    {
        if (culler != null)
            drawGpuCulledModelBatches(batches, programs, culler)
        else 
            drawModelBatches(batches, programs, view.modelBuffer.instanceIndexMode, view.modelBuffer.instanceIndexBuffer)
    }

    override fun destroy()
    {
        opaqueStaticProgram.destroy()
        opaqueSkinnedProgram.destroy()
        maskedStaticProgram.destroy()
        maskedSkinnedProgram.destroy()
    }

    fun draw(frame: WorldRenderFrame)
    {
        renderFrameQueue.submit(frame)
        increaseBatchSize()
    }
}