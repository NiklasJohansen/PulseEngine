package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet.ShaderVariant
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet.ShaderVariant.SKINNED
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet.ShaderVariant.STATIC
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject.Companion.INVALID_INSTANCE_INDEX
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class RenderPassBuilder(
    val frustumPlaneSets: Array<FrustumPlaneSet>,
    val gpuCullItemBatchIndices: StreamingIntBufferObject?,
    val gpuCullItemBatchIndexOffset: Int
) {
    internal val batches = DynamicList<RenderItemBatch>()
    internal var gpuCullInstanceCount = 0
        private set

    private val scratchItems = DynamicList<WorldRenderItem>(256)
    private var commandIndex = 0

    fun WorldRenderBucket.fill(
        from: DynamicList<WorldRenderItem>,
        sortFunc: ((a: WorldRenderItem, b: WorldRenderItem) -> Int)? = ::compareForBatching,
        preserveDrawOrder: Boolean = false,
        requiredView: Int = 0
    ) {
        val bucket = this
        val useGpuCulling = (gpuCullItemBatchIndices != null)
        
        bucket.clear(commandIndex)
        scratchItems.clear()

        if (from.isEmpty())
            return

        from.forEach()
        {
            if (requiredView != 0 && !it.isInView(requiredView))
                return@forEach

            val bounds = it.cullingBounds
            if (!useGpuCulling && bounds != null)
            {
                if (frustumPlaneSets.none { set -> set.intersectsAabb(bounds, it.transform) })
                    return@forEach // Skip items that are outside the view frustum when GPU culling is not used
            }

            scratchItems += it
        }

        if (sortFunc != null)
            scratchItems.sortWith(sortFunc)

        var lastBatch = null as RenderItemBatch?

        scratchItems.forEach()
        {
            val instanceIndex = it.gpuInstanceIndex
            if (instanceIndex == INVALID_INSTANCE_INDEX)
                throw IllegalStateException("Render item has not been uploaded to the GPU instance buffer")

            val shaderVariant = it.mesh.selectShaderVariant()
            val cullMode      = it.material?.cullMode ?: CullMode.BACK

            val canAppendToBatch =
                !preserveDrawOrder &&
                 lastBatch?.matches(it.mesh, shaderVariant, cullMode) == true &&
                 (useGpuCulling || lastBatch.instanceIndex + lastBatch.instanceCount == instanceIndex)

            if (canAppendToBatch)
            {
                lastBatch.instanceCount++
            }
            else
            {
                lastBatch = bucket.addBatch(it.mesh, shaderVariant, cullMode, instanceIndex, instanceCount = 1)
                batches += lastBatch
                commandIndex++
            }

            if (useGpuCulling)
            {
                gpuCullItemBatchIndices[gpuCullItemBatchIndexOffset + it.gpuCullItemIndex] = commandIndex - 1
                gpuCullInstanceCount++
            }
        }
    }

    private fun compareForBatching(a: WorldRenderItem, b: WorldRenderItem): Int
    {
        var result = System.identityHashCode(a.mesh) - System.identityHashCode(b.mesh)
        if (result != 0) return result

        result = a.mesh.selectShaderVariant().ordinal - b.mesh.selectShaderVariant().ordinal
        if (result != 0) return result

        return (a.material?.cullMode ?: CullMode.BACK).ordinal - (b.material?.cullMode ?: CullMode.BACK).ordinal
    }

    private fun Mesh.selectShaderVariant(): ShaderVariant
    {
        return if (skinningBounds != null) SKINNED else STATIC
    }
}