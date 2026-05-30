package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.objects.ModelBoneBuffer
import no.njoh.pulseengine.core.graphics.api.objects.ModelBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuModelCuller
import no.njoh.pulseengine.core.graphics.util.RenderItemBatcher
import org.joml.Vector3f

class WorldRenderView()
{
    val modelBuffer = ModelBufferObject()

    private var opaqueCuller: GpuModelCuller? = null
    private var maskedCuller: GpuModelCuller? = null

    private val opaqueBatcher = RenderItemBatcher()
    private val maskedBatcher = RenderItemBatcher()
    private val blendedBatcher = RenderItemBatcher()

    private val blendComparator = Comparator<RenderItem> { a, b -> compareForTransparency(a, b) }
    private val frustum = Frustum()
    private val camPos = Vector3f()
    private val tmpPos1 = Vector3f()
    private val tmpPos2 = Vector3f()

    private var initialized = false
    private var frame = null as WorldRenderFrame?
    private var frameVersion = -1
    private var hasSubmittedGpuData = false

    fun update(
        engine: PulseEngineInternal,
        camera: Camera,
        frame: WorldRenderFrame,
        boneBuffer: ModelBoneBuffer
    ): WorldRenderView {

        val newFrameVersion = frame.version
        if (this.frame === frame && frameVersion == newFrameVersion)
            return this // No new data to submit

        if (!initialized)
        {
            modelBuffer.init(boneBuffer)
            opaqueCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            maskedCuller = GpuModelCuller.createIfSupported()?.apply { init(engine) }
            initialized = true
        }

        camera.invViewMatrix.getTranslation(camPos)
        frustum.setForCamera(camera)

        modelBuffer.clear()
        opaqueCuller?.clear()
        maskedCuller?.clear()

        frame.blendedItems.sortWith(blendComparator)

        val opaqueBatches = opaqueBatcher.buildBatches(frame.opaqueItems, modelBuffer, frustum, opaqueCuller, sortForBatching = true)
        val maskedBatches = maskedBatcher.buildBatches(frame.maskedItems, modelBuffer, frustum, maskedCuller, sortForBatching = true)
        blendedBatcher.buildBatches(frame.blendedItems, modelBuffer, frustum, sortForBatching = false)

        modelBuffer.submit()
        opaqueCuller?.submitAndCull(opaqueBatches, frustum)
        maskedCuller?.submitAndCull(maskedBatches, frustum)

        this.frame = frame
        frameVersion = newFrameVersion
        hasSubmittedGpuData = true
        return this
    }

    fun isBuiltFrom(frame: WorldRenderFrame) = this.frame === frame

    fun markGpuDataInUse()
    {
        if (!hasSubmittedGpuData)
            return

        opaqueCuller?.markSubmittedDataInUse()
        maskedCuller?.markSubmittedDataInUse()
        modelBuffer.markSubmittedDataInUse()
        hasSubmittedGpuData = false
    }

    fun destroy()
    {
        opaqueCuller?.destroy()
        maskedCuller?.destroy()
        modelBuffer.destroy()
        initialized = false
        frame = null
        frameVersion = -1
        hasSubmittedGpuData = false
    }

    fun getOpaqueCuller() = opaqueCuller

    fun getMaskedCuller() = maskedCuller

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