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
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.EMPTY
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.primitives.GenerationalIntLookup

class DrawPayloadBuilder(
    var frustumPlaneSets: Array<FrustumPlaneSet> = emptyArray(),
    var gpuCullItemIndices: StreamingIntBufferObject? = null,
    var gpuCullItemIndexOffset: Int = 0,
    var gpuCullItemBatchIndices: StreamingIntBufferObject? = null,
    var gpuCullItemBatchIndexOffset: Int = 0, 
    var frustumPlaneSetCount: Int = frustumPlaneSets.size
) {
    internal val batches = DynamicList<DrawBatch>()
    internal var gpuCullInstanceCount = 0
        private set

    private val scratchItems = DynamicList<RenderItem>(256)
    private val batchIndexBySortKey = GenerationalIntLookup(128, NO_BATCH_INDEX)
    private val batchingSortFunc: (RenderItem, RenderItem) -> Int = ::compareForBatching
    private var commandIndex = 0

    fun clear()
    {
        batches.clear()
        scratchItems.clear()
        batchIndexBySortKey.clear()
        gpuCullInstanceCount = 0
        commandIndex = 0
    }

    fun RenderBucket.fill(
        from: DynamicList<RenderItem>,
        renderPassMask: RenderPassMask = EMPTY,
        sortFunc: ((a: RenderItem, b: RenderItem) -> Int)? = batchingSortFunc,
        preserveDrawOrder: Boolean = false,
    ) {
        val bucket = this
        val useGpuCulling = (gpuCullItemIndices != null && gpuCullItemBatchIndices != null)

        bucket.clear(commandIndex)

        if (from.isEmpty())
            return

        val useGpuKeyBatching = useGpuCulling && !preserveDrawOrder && sortFunc === batchingSortFunc
        if (useGpuKeyBatching)
        {
            bucket.batchGpuRenderItemsByKey(from, renderPassMask)
            return
        }

        scratchItems.clear()
        from.forEach()
        {
            if (renderPassMask != EMPTY && !it.isVisible(renderPassMask))
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

    private fun RenderBucket.batchGpuRenderItemsByKey(items: DynamicList<RenderItem>, renderPassMask: RenderPassMask)
    {
        prepareGpuCullItems(items.size)
        batchIndexBySortKey.clear()

        var lastBatchSortKey = INVALID_BATCH_SORT_KEY
        var lastBatchIndex = NO_BATCH_INDEX

        items.forEach()
        {
            if (renderPassMask != EMPTY && !it.isVisible(renderPassMask))
                return@forEach

            val instanceIndex = it.gpuInstanceIndex
            if (instanceIndex == INVALID_INSTANCE_INDEX)
                throw IllegalStateException("Render item has not been uploaded to the GPU instance buffer")

            val batchSortKey = it.batchSortKey
            var batchIndex = if (batchSortKey == lastBatchSortKey) lastBatchIndex else batchIndexBySortKey[batchSortKey]

            if (batchIndex == NO_BATCH_INDEX)
            {
                val shaderVariant = it.mesh.selectShaderVariant()
                val cullMode = it.material?.cullMode ?: CullMode.BACK
                val batch = addBatch(it.mesh, shaderVariant, cullMode, instanceIndex, instanceCount = 1)

                batchIndex = commandIndex++
                batchIndexBySortKey[batchSortKey] = batchIndex
                batches += batch
            }
            else
            {
                batches[batchIndex].instanceCount++
                bumpInstanceCount()
            }

            lastBatchSortKey = batchSortKey
            lastBatchIndex   = batchIndex
            appendGpuCullItem(it, batchIndex)
        }
    }

    private fun RenderBucket.batchRenderItems(items: DynamicList<RenderItem>, preserveDrawOrder: Boolean, useGpuCulling: Boolean) 
    {
        if (useGpuCulling) prepareGpuCullItems(items.size)

        var lastBatch = null as DrawBatch?
        var lastBatchSortKey = INVALID_BATCH_SORT_KEY

        items.forEach()
        {
            val instanceIndex = it.gpuInstanceIndex
            if (instanceIndex == INVALID_INSTANCE_INDEX)
                throw IllegalStateException("Render item has not been uploaded to the GPU instance buffer")

            val batchSortKey = it.batchSortKey
            val batch = lastBatch
            val canAppendToBatch = !preserveDrawOrder &&
                batch != null &&
                batchSortKey == lastBatchSortKey &&
                (useGpuCulling || batch.instanceIndex + batch.instanceCount == instanceIndex)

            if (canAppendToBatch)
            {
                batch.instanceCount++
                bumpInstanceCount()
            }
            else
            {
                val shaderVariant = it.mesh.selectShaderVariant()
                val cullMode      = it.material?.cullMode ?: CullMode.BACK
                val batch = addBatch(it.mesh, shaderVariant, cullMode, instanceIndex, instanceCount = 1)
                lastBatch = batch
                lastBatchSortKey = batchSortKey
                batches += batch
                commandIndex++
            }

            if (useGpuCulling)
                appendGpuCullItem(it, commandIndex - 1)
        }
    }

    private fun prepareGpuCullItems(itemCount: Int)
    {
        gpuCullItemIndices?.fill(itemCount) {}
        gpuCullItemBatchIndices?.fill(itemCount) {}
    }

    private fun appendGpuCullItem(item: RenderItem, batchIndex: Int)
    {
        gpuCullItemIndices?.put(item.gpuCullItemIndex)
        gpuCullItemBatchIndices?.put(batchIndex)
        gpuCullInstanceCount++
    }

    private fun compareForBatching(a: RenderItem, b: RenderItem): Int = a.batchSortKey.compareTo(b.batchSortKey)

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

    companion object
    {
        private const val NO_BATCH_INDEX = -1
        private const val INVALID_BATCH_SORT_KEY = -1
    }
}