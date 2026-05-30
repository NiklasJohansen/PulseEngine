package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.graphics.api.objects.ModelBoneBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuModelCullData
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class GpuFrameResource
{
    val modelBuffer = ModelBufferObject()
    val cullData = GpuModelCullData()
    var frame: WorldRenderFrame? = null 
        private set

    private var initialized = false
    private var frameVersion = -1
    private var hasSubmittedGpuData = false

    fun update(frame: WorldRenderFrame, boneBuffer: ModelBoneBufferObject): GpuFrameResource
    {
        if (!initialized)
        {
            modelBuffer.init(boneBuffer)
            initialized = true
        }

        if (this.frame === frame && frameVersion == frame.version)
        {
            modelBuffer.bindSubmittedRange()
            cullData.bindSubmittedRanges()
            return this
        }

        modelBuffer.clear()
        cullData.clear()

        frame.opaqueItems.uploadToModelBuffer()
        frame.maskedItems.uploadToModelBuffer()
        frame.blendedItems.uploadToModelBuffer()

        frame.opaqueItems.uploadCullData()
        frame.maskedItems.uploadCullData()
        frame.blendedItems.uploadCullData()

        modelBuffer.submit()
        cullData.submit()

        this.frame = frame
        this.frameVersion = frame.version
        this.hasSubmittedGpuData = true

        return this
    }

    fun markGpuDataInUse()
    {
        if (!hasSubmittedGpuData)
            return

        modelBuffer.markSubmittedDataInUse()
        cullData.markSubmittedDataInUse()
        hasSubmittedGpuData = false
    }

    fun destroy()
    {
        modelBuffer.destroy()
        cullData.destroy()
        initialized = false
        frame = null
        frameVersion = -1
        hasSubmittedGpuData = false
    }

    private fun DynamicList<RenderItem>.uploadToModelBuffer() =
        forEach { item -> item.gpuInstanceIndex = modelBuffer.addItem(item) }
    
    private fun DynamicList<RenderItem>.uploadCullData() =
        forEach { item -> cullData.addItem(item, item.gpuInstanceIndex) }
}