package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.RenderItemBatchList
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItemBatcher.buildBatches
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItemCullingData
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItemGpuCuller
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f

class WorldShadowRenderView(override val viewId: Int) : WorldRenderView
{
    val batches = RenderItemBatchList()

    override val culler = WorldRenderItemGpuCuller.createIfSupported()

    private val shadowFrustum = Frustum()
    private val allItems = DynamicList<WorldRenderItem>(1024)
    private var shadowCullingMatrix = Matrix4f()
    private var cascadeFrustumPlaneSets = arrayOf<Frustum.FrustumPlaneSet>()

    fun prepare(
        shadowCullingMatrix: Matrix4f,
        cascadeFrustumPlaneSets: Array<Frustum.FrustumPlaneSet>
    ) {
        this.shadowCullingMatrix = shadowCullingMatrix
        this.cascadeFrustumPlaneSets = cascadeFrustumPlaneSets
    }

    override fun update(
        engine: PulseEngineInternal, 
        scene: WorldRenderScene, 
        commandBufferStarIndex: Int, 
        cullData: WorldRenderItemCullingData
    ): Int = measure(
        label = "cull world shadow view"
    ) {
        culler?.init(engine)
        culler?.clear(cullData)

        shadowFrustum.setForViewProjection(shadowCullingMatrix)
        allItems.clear()
        allItems += scene.opaqueItems
        allItems += scene.maskedItems

        var commandStartIndex = if (culler != null) 0 else commandBufferStarIndex
        buildBatches(
            out = batches,
            items = allItems,
            frustum = shadowFrustum,
            gpuCuller = culler,
            sortForBatching = true,
            requiredView = viewId,
            commandStartIndex = commandStartIndex
        )

        commandStartIndex += batches.size
        culler?.submitAndCullCascades(batches, cascadeFrustumPlaneSets, cullData)

        return commandStartIndex
    }

    override fun finish()
    {
        culler?.markSubmittedDataInUse()
    }
    
    override fun clear()
    {
        batches.clear()
    }

    override fun destroy()
    {
        culler?.destroy()
    }
}