package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.*
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode

class InstanceBufferObject
{
    var instanceCount = 0; private set
    var instanceIndexMode = UNIFORM_OFFSET; private set
    var instanceIndexBuffer = null as StreamingIntBufferObject?; private set

    private lateinit var instanceBuffer: StreamingFloatBufferObject

    fun init()
    {
        if (this::instanceBuffer.isInitialized)
            return

        instanceBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(
            blockBinding = INSTANCE_BUFFER_BINDING,
            initCapacity = 20 * 512
        )

        instanceIndexMode = getSupportedModelInstanceIndexMode()
        if (instanceIndexMode == INSTANCE_ATTRIBUTE)
            instanceIndexBuffer = StreamingIntBufferObject.createArrayBuffer(initCapacity = 512)
    }

    fun clear()
    {
        instanceBuffer.clear()
        instanceIndexBuffer?.clear()
        instanceCount = 0
    }

    fun addItem(item: WorldRenderItem, boneOffsetIndex: Int): Int
    {
        val instanceIndex = instanceCount++
        val materialId = item.material?.id ?: Material.DEFAULT_ID

        instanceBuffer.fill(20) // 16 + 4
        {
            put(item.transform)
            put(materialId.toFloat(), boneOffsetIndex.toFloat(), 0f, 0f)
        }

        instanceIndexBuffer?.fill(1)
        {
            put(instanceIndex)
        }

        return instanceIndex
    }

    fun submit() = measure("submit instance buffers")
    {
        instanceBuffer.submit()
        instanceIndexBuffer?.submit()
    }

    fun markSubmittedDataInUse() = measure("fence instance buffers")
    {
        instanceBuffer.markSubmittedDataInUse()
        instanceIndexBuffer?.markSubmittedDataInUse()
    }

    fun destroy()
    {
        if (!this::instanceBuffer.isInitialized)
            return

        instanceBuffer.destroy()
        instanceIndexBuffer?.destroy()
    }

    companion object
    {
        const val INSTANCE_BUFFER_BINDING = 1
        const val INVALID_INSTANCE_INDEX = -1
    }
}