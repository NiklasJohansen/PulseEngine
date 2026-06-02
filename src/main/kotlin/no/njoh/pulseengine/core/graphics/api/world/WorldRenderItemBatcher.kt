package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.RenderItemBatchList
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.util.selectShaderVariant
import no.njoh.pulseengine.core.shared.primitives.DynamicList

object WorldRenderItemBatcher
{
    fun buildBatches(
        out: RenderItemBatchList,
        items: DynamicList<WorldRenderItem>,
        frustum: Frustum,
        gpuCuller: WorldRenderItemGpuCuller? = null,
        sortForBatching: Boolean = true,
        requiredView: Int = 0,
        commandStartIndex: Int = 0,
        preserveItemOrder: Boolean = false
    ): RenderItemBatchList {

        out.clear(commandStartIndex)
        if (items.isEmpty())
            return out

        if (sortForBatching)
            items.sortWith(::compareForBatching)

        items.forEach()
        {
            if (requiredView != 0 && !it.isInView(requiredView))
                return@forEach

            if (gpuCuller == null && it.cullable)
            {
                if (!frustum.intersectsAabb(it.cullingBounds, it.transform))
                    return@forEach // Skip items that are outside the view frustum when GPU culling is not used
            }

            val instanceIndex = it.gpuInstanceIndex
            if (!InstanceBufferObject.isValidInstanceIndex(instanceIndex))
                throw IllegalStateException("Render item has not been uploaded to the GPU instance buffer")

            val shaderVariant = it.model.selectShaderVariant()
            val cullMode      = it.material?.cullMode ?: Material.CullMode.BACK
            val lastBatch     = out.lastOrNull()

            val canAppendToBatch =
                !preserveItemOrder &&
                lastBatch?.matches(it.model, it.subMesh, shaderVariant, cullMode) == true &&
                (gpuCuller != null || lastBatch.instanceIndex + lastBatch.instanceCount == instanceIndex)

            if (canAppendToBatch)
                lastBatch.instanceCount++
            else
                out.add(it.model, it.subMesh, shaderVariant, cullMode, instanceIndex, instanceCount = 1)

            gpuCuller?.addInstance(it.gpuCullItemIndex, batchIndex = out.commandStartIndex + out.size - 1)
        }

        return out
    }

    private fun compareForBatching(a: WorldRenderItem, b: WorldRenderItem): Int
    {
        var result = System.identityHashCode(a.model) - System.identityHashCode(b.model)
        if (result != 0) return result

        result = a.subMesh.index - b.subMesh.index
        if (result != 0) return result

        result = a.model.selectShaderVariant().ordinal - b.model.selectShaderVariant().ordinal
        if (result != 0) return result

        return (a.material?.cullMode ?: Material.CullMode.BACK).ordinal - (b.material?.cullMode ?: Material.CullMode.BACK).ordinal
    }
}