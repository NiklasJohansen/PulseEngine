package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet.ShaderVariant
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.primitives.StaticList

class WorldRenderBucket(initialCapacity: Int = 128)
{
    private val batches = DynamicList<RenderItemBatch>(initialCapacity)

    var size = 0
        private set

    var commandStartIndex = 0
        private set
    
    fun clear(commandStartIndex: Int = 0)
    {
        this.size = 0
        this.commandStartIndex = commandStartIndex
    }

    fun addBatch(mesh: Mesh, shaderVariant: ShaderVariant, cullMode: CullMode, instanceIndex: Int, instanceCount: Int): RenderItemBatch
    {
        val batch = if (size < batches.size) batches[size] else RenderItemBatch().also { batches += it }
        batch.set(mesh, shaderVariant, cullMode, instanceIndex, instanceCount)
        size++
        return batch
    }

    fun totalInstanceCount(): Int
    {
        var count = 0
        for (i in 0 until size)
            count += batches[i].instanceCount
        return count
    }

    inline fun forEachBatch(action: (RenderItemBatch) -> Unit)
    {
        val batches = getBackingList()
        for (i in 0 until size) action(batches[i])
    }

    @PublishedApi
    internal fun getBackingList(): StaticList<RenderItemBatch> = batches
}