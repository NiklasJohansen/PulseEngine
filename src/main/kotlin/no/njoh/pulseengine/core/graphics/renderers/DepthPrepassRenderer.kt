package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.Attachment.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawGpuCulledModelBatches
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawModelBatches
import no.njoh.pulseengine.core.graphics.util.GpuModelCuller
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelBatcher
import no.njoh.pulseengine.core.graphics.util.addVisibleItems
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import no.njoh.pulseengine.core.shared.utils.Extensions.addAllNoAlloc
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.lwjgl.opengl.GL11.*

class DepthPrepassRenderer(override val order: Int = 20) : Renderer()
{
    private lateinit var staticProgram: ShaderProgram
    private lateinit var skinnedProgram: ShaderProgram
    private lateinit var modelBatcher: ModelBatcher

    private var gpuCuller: GpuModelCuller? = null

    private val modelBuffer    = ModelBufferObject()
    private val renderItems    = ArrayList<RenderItem>(1024)
    private var readDrawLists  = ArrayList<DrawList>()
    private var writeDrawLists = ArrayList<DrawList>()
    private val frustum        = Frustum()

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::staticProgram.isInitialized)
        {
            staticProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_depth.vert", ::transformModelVertexShader)),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_depth.frag"))
            )
            skinnedProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_depth_skinned.vert", ::transformModelVertexShader)),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_depth.frag"))
            )
            modelBatcher = ModelBatcher(staticProgram, skinnedProgram)
            gpuCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
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

        staticProgram.bind()
        staticProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        staticProgram.setUniform("viewProjection", surface.camera.viewProjectionMatrix)

        skinnedProgram.bind()
        skinnedProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        skinnedProgram.setUniform("viewProjection", surface.camera.viewProjectionMatrix)

        renderItems.clear()
        frustum.setForCamera(surface.camera)
        readDrawLists.forEachFast()
        {
            if (gpuCuller != null)
            {
                renderItems.addAllNoAlloc(it.opaqueItems)
                renderItems.addAllNoAlloc(it.maskedItems)
            }
            else
            {
                renderItems.addVisibleItems(it.opaqueItems, frustum)
                renderItems.addVisibleItems(it.maskedItems, frustum)
            }
        }

        modelBuffer.clear()
        gpuCuller?.clear()
        val batches = modelBatcher.createBatchesAndFillBuffer(renderItems, modelBuffer, gpuCuller)
        modelBuffer.submit()

        measure({"draw opaque + masked (" plus batches.totalInstanceCount() plus "i, " plus batches.size plus "b)"})
        {
            val culler = gpuCuller
            if (culler != null)
            {
                culler.submitAndCull(batches,frustum)
                drawGpuCulledModelBatches(batches, culler)
            }
            else drawModelBatches(batches, modelBuffer.instanceIndexMode, modelBuffer.instanceIndexBuffer)

            culler?.markSubmittedDataInUse()
            modelBuffer.markSubmittedDataInUse()
        }

        glColorMask(true, true, true, true)
        glDisable(GL_CULL_FACE)

        surface.renderTarget.resolveDepth(engine)
        surface.getTextures().firstOrNullFast { it.attachment == DEPTH_TEXTURE }?.generateMips(engine)
        surface.renderTarget.begin()
    }

    override fun destroy()
    {
        staticProgram.destroy()
        skinnedProgram.destroy()
        modelBuffer.destroy()
        gpuCuller?.destroy()
    }

    fun draw(drawList: DrawList)
    {
        writeDrawLists += drawList
        increaseBatchSize()
    }
}