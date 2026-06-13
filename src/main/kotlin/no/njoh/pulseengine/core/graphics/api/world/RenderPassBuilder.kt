package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model.Aabb
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet.ShaderVariant
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet.ShaderVariant.SKINNED
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet.ShaderVariant.STATIC
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject.Companion.INVALID_INSTANCE_INDEX
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f

class RenderPassBuilder(
    val frustumPlaneSets: Array<FrustumPlaneSet>,
    val gpuCullItemIndices: StreamingIntBufferObject?,
    val gpuCullItemIndexOffset: Int,
    val gpuCullItemBatchIndices: StreamingIntBufferObject?,
    val gpuCullItemBatchIndexOffset: Int,
    val frustumPlaneSetCount: Int = frustumPlaneSets.size
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
        requiredView: Int = 0,
    ) {
        val bucket = this
        val useGpuCulling = (gpuCullItemIndices != null && gpuCullItemBatchIndices != null)

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
                if (intersectsAnyFrustumPlaneSet(bounds, it.transform))
                    scratchItems += it
            }
            else scratchItems += it
        }

        if (sortFunc != null)
            scratchItems.sortWith(sortFunc)

        bucket.appendScratchItems(scratchItems, preserveDrawOrder, useGpuCulling)
    }

    private fun WorldRenderBucket.appendScratchItems(
        items: DynamicList<WorldRenderItem>,
        preserveDrawOrder: Boolean,
        useGpuCulling: Boolean
    ) {
        if (useGpuCulling)
        {
            // Ensure space for all items in the bucket
            gpuCullItemIndices?.fill(scratchItems.size) {}
            gpuCullItemBatchIndices?.fill(scratchItems.size) {}
        }

        var lastBatch = null as RenderItemBatch?

        items.forEach()
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
                val batch = addBatch(it.mesh, shaderVariant, cullMode, instanceIndex, instanceCount = 1)
                lastBatch = batch
                batches += batch
                commandIndex++
            }

            if (useGpuCulling)
            {
                gpuCullItemIndices?.put(it.gpuCullItemIndex)
                gpuCullItemBatchIndices?.put(commandIndex - 1)
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

    private fun intersectsAnyFrustumPlaneSet(bounds: Aabb, transform: Matrix4f): Boolean
    {
        for (i in 0 until frustumPlaneSetCount)
        {
            if (frustumPlaneSets[i].intersectsAabb(bounds, transform)) return true
        }
        return false
    }

    private fun Mesh.selectShaderVariant(): ShaderVariant
    {
        return if (skinningBounds != null) SKINNED else STATIC
    }
}