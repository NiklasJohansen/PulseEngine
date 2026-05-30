package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.RenderItem
import no.njoh.pulseengine.core.graphics.api.ModelBatchList
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class RenderItemBatcher
{
    private val batchList = ModelBatchList()
    private val batchComparator = Comparator<RenderItem> { a, b -> compareForBatching(a, b) }

    fun buildBatches(
        items: DynamicList<RenderItem>,
        modelBuffer: ModelBufferObject,
        frustum: Frustum,
        gpuCuller: GpuModelCuller? = null,
        sortForBatching: Boolean = true
    ): ModelBatchList {

        batchList.clear()
        if (items.isEmpty())
            return batchList

        if (sortForBatching)
            items.sortWith(batchComparator)

        items.forEach()
        {
            if (gpuCuller == null && it.cullable)
            {
                if (!frustum.intersectsAabb(it.cullingBounds, it.transform))
                    return@forEach // Skip items that are outside the view frustum when GPU culling is not used
            }

            val instanceIndex = modelBuffer.addItem(it)

            val shaderVariant = it.model.selectShaderVariant()
            val cullMode      = it.material?.cullMode ?: Material.CullMode.BACK
            val lastBatch     = batchList.lastOrNull()

            if (lastBatch?.matches(it.model, it.subMesh, shaderVariant, cullMode) == true)
                lastBatch.instanceCount++
            else
                batchList.add(it.model, it.subMesh, shaderVariant, cullMode, instanceIndex, instanceCount = 1)

            gpuCuller?.addInstance(it, instanceIndex, batchIndex = batchList.size - 1)
        }
  
        return batchList
    }

    private fun compareForBatching(a: RenderItem, b: RenderItem): Int
    {
        var result = System.identityHashCode(a.model) - System.identityHashCode(b.model)
        if (result != 0) return result

        result = a.subMesh.index - b.subMesh.index
        if (result != 0) return result

        result = a.model.selectShaderVariant().ordinal - b.model.selectShaderVariant().ordinal
        if (result != 0) return result

        return (a.material?.cullMode ?: Material.CullMode.BACK).ordinal - (b.material?.cullMode ?: Material.CullMode.BACK).ordinal
    }

    fun getBuiltBatches(): ModelBatchList = batchList
}