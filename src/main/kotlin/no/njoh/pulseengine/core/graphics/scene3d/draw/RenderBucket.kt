package no.njoh.pulseengine.core.graphics.scene3d.draw

import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.primitives.StaticList

class RenderBucket(initialCapacity: Int = 128)
{
    private val batches = DynamicList<DrawBatch>(initialCapacity)

    var size              = 0; private set
    var instanceCount     = 0; private set
    var commandStartIndex = 0; private set

    fun clear(commandStartIndex: Int = 0)
    {
        this.size = 0
        this.instanceCount = 0
        this.commandStartIndex = commandStartIndex
    }

    fun addBatch(mesh: Mesh, shaderVariant: ShaderVariant, cullMode: CullMode, instanceIndex: Int, instanceCount: Int): DrawBatch
    {
        val batch = if (size < batches.size) batches[size] else DrawBatch().also { batches += it }
        batch.set(mesh, shaderVariant, cullMode, instanceIndex, instanceCount)
        bumpInstanceCount(instanceCount)
        this.size++
        return batch
    }

    fun bumpInstanceCount(amount: Int = 1)
    {
        instanceCount += amount
    }

    inline fun forEachBatch(action: (DrawBatch) -> Unit)
    {
        val batches = getBackingList()
        for (i in 0 until size) action(batches[i])
    }

    @PublishedApi
    internal fun getBackingList(): StaticList<DrawBatch> = batches
}