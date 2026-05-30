package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuModelCuller
import no.njoh.pulseengine.core.graphics.util.RenderItemBatcher
import no.njoh.pulseengine.core.graphics.api.WorldRenderFrame.Companion.CAMERA_PASS
import org.joml.Vector3f

class WorldRenderCameraView()
{
    var frame: WorldRenderFrame? = null
        private set

    val modelBuffer: ModelBufferObject
        get() = gpuResource?.modelBuffer ?: throw IllegalStateException("World render view has not been prepared")

    private var gpuCuller: GpuModelCuller? = null

    private val opaqueBatcher  = RenderItemBatcher()
    private val maskedBatcher  = RenderItemBatcher()
    private val blendedBatcher = RenderItemBatcher()

    private val blendComparator = Comparator<RenderItem> { a, b -> compareForTransparency(a, b) }
    private val frustum = Frustum()
    private val camPos = Vector3f()
    private val tmpPos1 = Vector3f()
    private val tmpPos2 = Vector3f()

    private var initialized = false
    private var gpuResource = null as GpuFrameResource?
    private var camera = null as Camera?

    private var frameVersion = -1
    private var hasSubmittedGpuData = false

    fun update(engine: PulseEngineInternal, camera: Camera, frame: WorldRenderFrame, gpuResource: GpuFrameResource): WorldRenderCameraView 
    {
        val newFrameVersion = frame.version
        if (this.frame === frame && this.camera === camera && frameVersion == newFrameVersion)
        {
            gpuResource.modelBuffer.bindSubmittedRange()
            return this // No new data to submit
        }

        if (!initialized)
        {
            gpuCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            initialized = true
        }

        camera.invViewMatrix.getTranslation(camPos)
        frustum.setForCamera(camera)

        gpuCuller?.clear(gpuResource.cullData)

        frame.blendedItems.sortWith(blendComparator)

        val opaqueBatches = opaqueBatcher.buildBatches(
            items = frame.opaqueItems,
            frustum = frustum,
            gpuCuller = gpuCuller,
            sortForBatching = true,
            requiredRenderPass = CAMERA_PASS,
            commandStartIndex = 0
        )
        
        val maskedBatches = maskedBatcher.buildBatches(
            items = frame.maskedItems,
            frustum = frustum,
            gpuCuller = gpuCuller,
            sortForBatching = true,
            requiredRenderPass = CAMERA_PASS,
            commandStartIndex = opaqueBatches.size
        )
        
        val blendedBatches = blendedBatcher.buildBatches(
            items = frame.blendedItems,
            frustum = frustum,
            gpuCuller = gpuCuller,
            sortForBatching = false,
            requiredRenderPass = CAMERA_PASS,
            commandStartIndex = opaqueBatches.size + maskedBatches.size,
            preserveItemOrder = true
        )
 
        gpuCuller?.submitAndCull(listOf(opaqueBatches, maskedBatches, blendedBatches), frustum, gpuResource.cullData)

        this.gpuResource = gpuResource
        this.camera = camera
        this.frame = frame
        this.frameVersion = newFrameVersion
        this.hasSubmittedGpuData = true

        return this
    }

    fun markGpuDataInUse()
    {
        if (!hasSubmittedGpuData)
            return

        gpuCuller?.markSubmittedDataInUse()
        hasSubmittedGpuData = false
    }

    fun destroy()
    {
        gpuCuller?.destroy()
        initialized = false
        gpuResource = null
        camera = null
        frame = null
        frameVersion = -1
        hasSubmittedGpuData = false
    }

    fun getOpaqueCuller() = gpuCuller

    fun getMaskedCuller() = gpuCuller

    fun getBlendedCuller() = gpuCuller

    fun getOpaqueBatches() = opaqueBatcher.getBuiltBatches()

    fun getMaskedBatches() = maskedBatcher.getBuiltBatches()

    fun getBlendedBatches() = blendedBatcher.getBuiltBatches()

    private fun compareForTransparency(a: RenderItem, b: RenderItem): Int
    {
        val aDist = camPos.distanceSquared(a.transform.getTranslation(tmpPos1))
        val bDist = camPos.distanceSquared(b.transform.getTranslation(tmpPos2))
        return bDist.compareTo(aDist)
    }
}