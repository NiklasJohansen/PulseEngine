package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuModelCuller
import no.njoh.pulseengine.core.graphics.util.RenderItemBatcher
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.graphics.api.WorldRenderFrame.Companion.SHADOW_PASS
import org.joml.Matrix4f

class WorldRenderShadowView()
{
    var frame: WorldRenderFrame? = null
        private set

    val modelBuffer: ModelBufferObject
        get() = gpuResource?.modelBuffer ?: throw IllegalStateException("World shadow render view has not been prepared")

    private var gpuCuller: GpuModelCuller? = null

    private val batcher = RenderItemBatcher()
    private val allItems = DynamicList<RenderItem>(1024)
    private val shadowFrustum = Frustum()

    private var initialized = false
    private var gpuResource = null as GpuFrameResource?
    private var hasSubmittedGpuData = false

    fun update(
        engine: PulseEngineInternal,
        frame: WorldRenderFrame,
        gpuResource: GpuFrameResource,
        shadowCullingMatrix: Matrix4f,
        cascadeFrustumPlaneSets: Array<Frustum.FrustumPlaneSet>
    ): WorldRenderShadowView {

        if (!initialized)
        {
            gpuCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            initialized = true
        }

        shadowFrustum.setForViewProjection(shadowCullingMatrix)

        gpuCuller?.clear(gpuResource.cullData)
        allItems.clear()

        allItems += frame.opaqueItems
        allItems += frame.maskedItems

        val batches = batcher.buildBatches(
            items = allItems,
            frustum = shadowFrustum,
            gpuCuller = gpuCuller,
            sortForBatching = true,
            requiredRenderPass = SHADOW_PASS
        )

        gpuCuller?.submitAndCullCascades(batches, cascadeFrustumPlaneSets, gpuResource.cullData)

        this.frame = frame
        this.gpuResource = gpuResource
        this.hasSubmittedGpuData = true
 
        return this
    }

    fun markGpuDataInUse()
    {
        if (!hasSubmittedGpuData)
            return

        gpuCuller?.markSubmittedDataInUse()
        hasSubmittedGpuData = false
    }

    fun destroy()
    {
        gpuCuller?.destroy()
        initialized = false
        gpuResource = null
        hasSubmittedGpuData = false
    }

    fun getGpuCuller() = gpuCuller

    fun getBatches() = batcher.getBuiltBatches()
}