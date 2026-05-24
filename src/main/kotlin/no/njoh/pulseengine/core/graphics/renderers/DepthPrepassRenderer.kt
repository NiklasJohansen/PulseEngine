package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.ModelBatchList
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.addAllVisible
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawGpuCulledModelBatches
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawModelBatches
import no.njoh.pulseengine.core.graphics.util.GpuModelCuller
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelBatcher
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.lwjgl.opengl.GL11.*

class DepthPrepassRenderer(override val order: Int = 20) : Renderer()
{
    private lateinit var opaqueStaticProgram: ShaderProgram
    private lateinit var opaqueSkinnedProgram: ShaderProgram
    private lateinit var maskedStaticProgram: ShaderProgram
    private lateinit var maskedSkinnedProgram: ShaderProgram
    private lateinit var opaqueBatcher: ModelBatcher
    private lateinit var maskedBatcher: ModelBatcher

    private var opaqueCuller: GpuModelCuller? = null
    private var maskedCuller: GpuModelCuller? = null

    private val modelBuffer    = ModelBufferObject()
    private val opaqueItems    = ArrayList<RenderItem>(1024)
    private val maskedItems    = ArrayList<RenderItem>(256)
    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()
    private val frustum        = Frustum()

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

            opaqueBatcher = ModelBatcher(opaqueStaticProgram, opaqueSkinnedProgram)
            maskedBatcher = ModelBatcher(maskedStaticProgram, maskedSkinnedProgram)
            opaqueCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            maskedCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            modelBuffer.init()
        }
    }

    override fun onInitFrame()
    {
        writeDrawLists = readDrawLists.also { readDrawLists = writeDrawLists }
        writeDrawLists.clear()
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

        frustum.setForCamera(surface.camera)
        
        opaqueItems.clear()
        maskedItems.clear()
        modelBuffer.clear()
        opaqueCuller?.clear()
        maskedCuller?.clear()

        readDrawLists.forEachFast()
        {
            opaqueItems.addAllVisible(it.opaqueItems, frustum = if (opaqueCuller == null) frustum else null)
            maskedItems.addAllVisible(it.maskedItems, frustum = if (maskedCuller == null) frustum else null)
        }

        val opaqueBatches = opaqueBatcher.createBatchesAndFillBuffer(opaqueItems, modelBuffer, opaqueCuller)
        val maskedBatches = maskedBatcher.createBatchesAndFillBuffer(maskedItems, modelBuffer, maskedCuller)
        modelBuffer.submit()

        val opaqueCount = opaqueBatches.totalInstanceCount()
        if (opaqueCount > 0)
        {
            configureOpaqueProgram(opaqueStaticProgram, surface)
            configureOpaqueProgram(opaqueSkinnedProgram, surface)
            measure({"opaque depth (" plus opaqueCount plus "i, " plus opaqueBatches.size plus "b)"})
            {
                drawBatches(opaqueBatches, opaqueCuller)
            }
        }

        val maskedCount = maskedBatches.totalInstanceCount()
        if (maskedCount > 0)
        {
            configureMaskedProgram(maskedStaticProgram, engine, surface)
            configureMaskedProgram(maskedSkinnedProgram, engine, surface)
            measure({"masked depth (" plus maskedCount plus "i, " plus maskedBatches.size plus "b)"})
            {
                drawBatches(maskedBatches, maskedCuller)
            }
        }

        opaqueCuller?.markSubmittedDataInUse()
        maskedCuller?.markSubmittedDataInUse()
        modelBuffer.markSubmittedDataInUse()

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

    private fun drawBatches(batches: ModelBatchList, culler: GpuModelCuller?)
    {
        if (culler != null)
        {
            culler.submitAndCull(batches, frustum)
            drawGpuCulledModelBatches(batches, culler)
        }
        else drawModelBatches(batches, modelBuffer.instanceIndexMode, modelBuffer.instanceIndexBuffer)
    }

    override fun destroy()
    {
        opaqueStaticProgram.destroy()
        opaqueSkinnedProgram.destroy()
        maskedStaticProgram.destroy()
        maskedSkinnedProgram.destroy()
        modelBuffer.destroy()
        opaqueCuller?.destroy()
        maskedCuller?.destroy()
    }

    fun draw(drawList: DrawList)
    {
        writeDrawLists += drawList
        increaseBatchSize()
    }
}