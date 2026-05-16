package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.api.ModelBatchList
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.quickSort

internal class ModelBatcher(
    var staticProgram: ShaderProgram,
    var skinnedProgram: ShaderProgram
) {
    private val batchList = ModelBatchList()
    private val batchComparator = Comparator<RenderItem> { a, b -> compareForBatching(a, b) }

    fun createBatchesAndFillBuffer(items: ArrayList<RenderItem>, modelBuffer: ModelBufferObject, sortForBatching: Boolean = true): ModelBatchList 
    {
        batchList.clear()
        if (items.isEmpty())
            return batchList

        if (sortForBatching)
            items.quickSort(batchComparator)

        items.forEachFast()
        {
            val instanceIndex = modelBuffer.addItem(it)

            val program   = it.model.selectProgram(skinnedProgram, staticProgram)
            val cullMode  = it.material?.cullMode ?: Material.CullMode.BACK
            val lastBatch = batchList.lastOrNull()

            if (lastBatch?.matches(it.model, it.subMesh, program, cullMode) == true)
                lastBatch.instanceCount++
            else 
                batchList.add(it.model, it.subMesh, program, cullMode, instanceIndex, instanceCount = 1)
        }

        return batchList
    }

    private fun compareForBatching(a: RenderItem, b: RenderItem): Int
    {
        var result = System.identityHashCode(a.model) - System.identityHashCode(b.model)
        if (result != 0) return result

        result = a.subMesh.index - b.subMesh.index
        if (result != 0) return result

        result = a.model.selectProgram(skinnedProgram, staticProgram).id - b.model.selectProgram(skinnedProgram, staticProgram).id
        if (result != 0) return result

        return (a.material?.cullMode ?: Material.CullMode.BACK).ordinal - (b.material?.cullMode ?: Material.CullMode.BACK).ordinal
    }
}