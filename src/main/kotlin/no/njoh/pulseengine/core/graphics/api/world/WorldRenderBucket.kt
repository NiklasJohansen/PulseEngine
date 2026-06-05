package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject.Companion.INVALID_INSTANCE_INDEX
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderDrawPayload.EmptyDrawPayload
import no.njoh.pulseengine.core.graphics.util.selectShaderVariant
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.primitives.StaticList

class WorldRenderBucket(initialCapacity: Int = 128)
{
    var drawPayload: WorldRenderDrawPayload = EmptyDrawPayload

    var size = 0
        private set

    var commandStartIndex = 0
        private set

    private val batches = DynamicList<RenderItemBatch>(initialCapacity)
    
    fun clear(commandStartIndex: Int = 0)
    {
        this.size = 0
        this.commandStartIndex = commandStartIndex
        this.drawPayload = EmptyDrawPayload
    }

    fun fill(
        drawBuffer: WorldRenderDrawBuffer,
        items: DynamicList<WorldRenderItem>,
        commandStartIndex: Int,
        frustum: Frustum,
        sortFunc: ((a: WorldRenderItem, b: WorldRenderItem) -> Int)? = ::compareForBatching,
        preserveItemOrder: Boolean = false,
        requiredView: Int = 0
    ) {
        clear(commandStartIndex)
        if (items.isEmpty()) 
            return

        if (sortFunc != null)
            items.sortWith(sortFunc)
        
        val useGpuCulling = drawBuffer.gpuCullingSupported
        
        items.forEach()
        {
            if (requiredView != 0 && !it.isInView(requiredView))
                return@forEach

            if (!useGpuCulling && it.cullable)
            {
                if (!frustum.intersectsAabb(it.cullingBounds, it.transform))
                    return@forEach // Skip items that are outside the view frustum when GPU culling is not used
            }

            val instanceIndex = it.gpuInstanceIndex
            if (instanceIndex == INVALID_INSTANCE_INDEX)
                throw IllegalStateException("Render item has not been uploaded to the GPU instance buffer")

            val shaderVariant = it.mesh.selectShaderVariant()
            val cullMode      = it.material?.cullMode ?: CullMode.BACK
            val lastBatch     = lastOrNull()

            val canAppendToBatch =
                !preserveItemOrder &&
                 lastBatch?.matches(it.model, it.subMesh, shaderVariant, cullMode) == true &&
                 (useGpuCulling || lastBatch.instanceIndex + lastBatch.instanceCount == instanceIndex)

            if (canAppendToBatch)
            {
                lastBatch.instanceCount++
            }
            else
            {
                val batch = if (size < batches.size) batches[size] else RenderItemBatch().also { batches += it }
                batch.set(it.model, it.subMesh, shaderVariant, cullMode, instanceIndex, instanceCount = 1)
                batch.set(it.mesh, shaderVariant, cullMode, instanceIndex, instanceCount = 1)
                size++
            }

            if (useGpuCulling)
                drawBuffer.addCullCandidate(it.gpuCullItemIndex, commandStartIndex + size - 1)
        }
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

    fun lastOrNull() = if (size == 0) null else batches[size - 1]

    @PublishedApi
    internal fun getBackingList(): StaticList<RenderItemBatch> = batches

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