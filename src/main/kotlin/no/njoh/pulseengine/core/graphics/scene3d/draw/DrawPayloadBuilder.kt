package no.njoh.pulseengine.core.graphics.scene3d.draw

import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model.Aabb
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.shared.primitives.Mat4f
import no.njoh.pulseengine.core.graphics.scene3d.view.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant.SKINNED
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant.STATIC
import no.njoh.pulseengine.core.graphics.gpu.buffer.InstanceBufferObject.Companion.INVALID_INSTANCE_INDEX
import no.njoh.pulseengine.core.graphics.gpu.buffer.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class DrawPayloadBuilder(
    val frustumPlaneSets: Array<FrustumPlaneSet>,
    val gpuCullItemIndices: StreamingIntBufferObject?,
    val gpuCullItemIndexOffset: Int,
    val gpuCullItemBatchIndices: StreamingIntBufferObject?,
    val gpuCullItemBatchIndexOffset: Int,
    val frustumPlaneSetCount: Int = frustumPlaneSets.size
) {
    internal val batches = DynamicList<DrawBatch>()
    internal var gpuCullInstanceCount = 0
        private set

    private val scratchItems = DynamicList<RenderItem>(256)
    private var commandIndex = 0

    fun RenderBucket.fill(
        from: DynamicList<RenderItem>,
        sortFunc: ((a: RenderItem, b: RenderItem) -> Int)? = ::compareForBatching,
        preserveDrawOrder: Boolean = false,
        requiredVisibility: Int = 0
    ) {
        val bucket = this
        val useGpuCulling = (gpuCullItemIndices != null && gpuCullItemBatchIndices != null)

        bucket.clear(commandIndex)
        scratchItems.clear()

        if (from.isEmpty())
            return

        from.forEach()
        {
            if (requiredVisibility != 0 && !it.isVisible(requiredVisibility))
                return@forEach

            if (!useGpuCulling && it.cullingBounds != null)
            {
                if (intersectsAnyFrustumPlaneSet(it.cullingBounds!!, it.transform))
                    scratchItems += it
            }
            else scratchItems += it
        }

        if (sortFunc != null)
            scratchItems.sortWith(sortFunc)

        bucket.batchRenderItems(scratchItems, preserveDrawOrder, useGpuCulling)
    }

    private fun RenderBucket.batchRenderItems(
        items: DynamicList<RenderItem>,
        preserveDrawOrder: Boolean,
        useGpuCulling: Boolean
    ) {
        if (useGpuCulling)
        {
            // Ensure space for all items in the bucket
            gpuCullItemIndices?.fill(scratchItems.size) {}
            gpuCullItemBatchIndices?.fill(scratchItems.size) {}
        }

        var lastBatch = null as DrawBatch?

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

    private fun compareForBatching(a: RenderItem, b: RenderItem): Int
    {
        var result = System.identityHashCode(a.mesh) - System.identityHashCode(b.mesh)
        if (result != 0) return result

        result = a.mesh.selectShaderVariant().ordinal - b.mesh.selectShaderVariant().ordinal
        if (result != 0) return result

        return (a.material?.cullMode ?: CullMode.BACK).ordinal - (b.material?.cullMode ?: CullMode.BACK).ordinal
    }

    private fun intersectsAnyFrustumPlaneSet(bounds: Aabb, transform: Mat4f): Boolean
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