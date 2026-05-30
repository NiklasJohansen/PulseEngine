package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.objects.ModelBoneBuffer
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuModelCuller
import no.njoh.pulseengine.core.graphics.util.RenderItemBatcher
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f

class WorldShadowRenderView()
{
    val modelBuffer = ModelBufferObject()

    private var gpuCuller: GpuModelCuller? = null

    private val batcher = RenderItemBatcher()
    private val allItems = DynamicList<RenderItem>(1024)
    private val shadowFrustum = Frustum()

    private var initialized = false
    private var hasSubmittedGpuData = false

    fun update(
        engine: PulseEngineInternal,
        frame: WorldRenderFrame,
        boneBuffer: ModelBoneBuffer,
        shadowCullingMatrix: Matrix4f,
        cascadeFrustumPlaneSets: Array<Frustum.FrustumPlaneSet>
    ): WorldShadowRenderView {

        if (!initialized)
        {
            modelBuffer.init(boneBuffer)
            gpuCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            initialized = true
        }

        shadowFrustum.setForViewProjection(shadowCullingMatrix)

        modelBuffer.clear()
        gpuCuller?.clear()
        allItems.clear()

        allItems += frame.opaqueItems
        allItems += frame.maskedItems

        val batches = batcher.buildBatches(allItems, modelBuffer, shadowFrustum, gpuCuller, sortForBatching = true)

        modelBuffer.submit()
        gpuCuller?.submitAndCullCascades(batches, cascadeFrustumPlaneSets)

        hasSubmittedGpuData = true
        return this
    }

    fun markGpuDataInUse()
    {
        if (!hasSubmittedGpuData)
            return

        gpuCuller?.markSubmittedDataInUse()
        modelBuffer.markSubmittedDataInUse()
        hasSubmittedGpuData = false
    }

    fun destroy()
    {
        gpuCuller?.destroy()
        modelBuffer.destroy()
        initialized = false
        hasSubmittedGpuData = false
    }

    fun getGpuCuller() = gpuCuller

    fun getBatches() = batcher.getBuiltBatches()
}