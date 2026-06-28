package no.njoh.pulseengine.core.graphics.gpu.buffer

import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure

class CullingBufferObject
{
    private lateinit var cullItemBuffer: StreamingIntBufferObject
    private lateinit var dynamicBoundsBuffer: StreamingFloatBufferObject

    var size = 0
        private set

    private var dynamicBoundsCount = 0

    fun init()
    {
        cullItemBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CULL_ITEM_BUFFER_BINDING, CULL_ITEM_INTS * 512, BUFFER_SEGMENTS)
        dynamicBoundsBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(DYNAMIC_BOUNDS_BUFFER_BINDING, DYNAMIC_BOUNDS_FLOATS * 128, BUFFER_SEGMENTS)
    }

    fun clear()
    {
        size = 0
        dynamicBoundsCount = 0
        cullItemBuffer.clear()
        dynamicBoundsBuffer.clear()
    }

    fun addItem(item: RenderItem)
    {
        val bounds = item.cullingBounds
        val flags = if (bounds == null) CULL_FLAG_ALWAYS_VISIBLE else 0
        val boundsIndex = if (bounds != null && item.needsDynamicGpuBounds())
        {
            val boundsIndex = dynamicBoundsCount++
            dynamicBoundsBuffer.fill(DYNAMIC_BOUNDS_FLOATS)
            {
                put((bounds.xMin + bounds.xMax) * 0.5f) // X center
                put((bounds.yMin + bounds.yMax) * 0.5f) // Y center
                put((bounds.zMin + bounds.zMax) * 0.5f) // Z center
                put((bounds.xMax - bounds.xMin) * 0.5f) // X half
                put((bounds.yMax - bounds.yMin) * 0.5f) // Y half
                put((bounds.zMax - bounds.zMin) * 0.5f) // Z half
                put(0f)
                put(0f)
            }
            boundsIndex
        }
        else STATIC_BOUNDS_INDEX

        item.gpuCullItemIndex = size
        cullItemBuffer.fill(CULL_ITEM_INTS)
        {
            put(item.mesh.gpuMetadataIndex)
            put(item.gpuInstanceIndex)
            put(boundsIndex)
            put(flags)
        }
        size++
    }

    fun submit() = measure("Culling buffers")
    {
        cullItemBuffer.submit()
        dynamicBoundsBuffer.submit()
    }

    fun bindSubmittedRanges()
    {
        cullItemBuffer.bindSubmittedRange()
        dynamicBoundsBuffer.bindSubmittedRange()
    }

    fun markSubmittedDataInUse() = measure("Culling buffers")
    {
        cullItemBuffer.markSubmittedDataInUse()
        dynamicBoundsBuffer.markSubmittedDataInUse()
    }

    fun destroy()
    {
        cullItemBuffer.destroy()
        dynamicBoundsBuffer.destroy()
    }

    private fun RenderItem.needsDynamicGpuBounds() =
        cullingBounds != null && !usesGpuSkinnedBounds() && (boneMatrices != null || cullingBounds !== mesh.localBounds)

    private fun RenderItem.usesGpuSkinnedBounds() =
        boneMatrices != null && mesh.skinningBounds != null

    companion object
    {
        private const val BUFFER_SEGMENTS = 6
        private const val CULL_ITEM_BUFFER_BINDING = 5
        private const val DYNAMIC_BOUNDS_BUFFER_BINDING = 8
        private const val STATIC_BOUNDS_INDEX = -1
        private const val CULL_ITEM_INTS = 4
        private const val DYNAMIC_BOUNDS_FLOATS = 8
        private const val CULL_FLAG_ALWAYS_VISIBLE = 1
    }
}