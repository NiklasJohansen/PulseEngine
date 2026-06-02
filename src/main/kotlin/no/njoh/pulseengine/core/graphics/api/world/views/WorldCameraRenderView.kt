package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.CameraProjectionType.PERSPECTIVE
import no.njoh.pulseengine.core.graphics.api.DefaultCamera
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.RenderItemBatchList
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItemBatcher.buildBatches
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItemCullingData
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItemGpuCuller
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import org.joml.Vector3f

class WorldCameraRenderView(override val viewId: Int) : WorldRenderView 
{
    val opaqueBatches  = RenderItemBatchList()
    val maskedBatches  = RenderItemBatchList()
    val blendedBatches = RenderItemBatchList()

    override val culler = WorldRenderItemGpuCuller.createIfSupported()

    private var camera: Camera = DefaultCamera(PERSPECTIVE)
    private val frustum = Frustum()
    private val camPos  = Vector3f()
    private val tmpPos1 = Vector3f()
    private val tmpPos2 = Vector3f()

    fun prepare(camera: Camera)
    {
        this.camera = camera
    }

    override fun update(
        engine: PulseEngineInternal, 
        scene: WorldRenderScene, 
        commandBufferStarIndex: Int, 
        cullData: WorldRenderItemCullingData
    ): Int = measure(
        label = "cull world camera view"
    ) {
        culler?.init(engine)
        culler?.clear(cullData)
        
        camera.invViewMatrix.getTranslation(camPos)
        frustum.setForCamera(camera)

        scene.blendedItems.sortWith(::compareForTransparency)

        var commandStartIndex = if (culler != null) 0 else commandBufferStarIndex
        buildBatches(
            out = opaqueBatches,
            items = scene.opaqueItems,
            frustum = frustum,
            gpuCuller = culler,
            sortForBatching = true,
            requiredView = viewId,
            commandStartIndex = commandStartIndex
        )

        commandStartIndex += opaqueBatches.size
        buildBatches(
            out = maskedBatches,
            items = scene.maskedItems,
            frustum = frustum,
            gpuCuller = culler,
            sortForBatching = true,
            requiredView = viewId,
            commandStartIndex = commandStartIndex
        )

        commandStartIndex += maskedBatches.size
        buildBatches(
            out = blendedBatches,
            items = scene.blendedItems,
            frustum = frustum,
            gpuCuller = culler,
            sortForBatching = false,
            requiredView = viewId,
            commandStartIndex = commandStartIndex,
            preserveItemOrder = true
        )

        commandStartIndex += blendedBatches.size
        culler?.submitAndCull(listOf(opaqueBatches, maskedBatches, blendedBatches), frustum, cullData)

        return commandStartIndex
    }

    override fun finish()
    {
        culler?.markSubmittedDataInUse()
    }

    override fun clear()
    {
        opaqueBatches.clear()
        maskedBatches.clear()
        blendedBatches.clear()
    }

    override fun destroy()
    {
        culler?.destroy()
    }
    
    private fun compareForTransparency(a: WorldRenderItem, b: WorldRenderItem): Int
    {
        val aDist = camPos.distanceSquared(a.transform.getTranslation(tmpPos1))
        val bDist = camPos.distanceSquared(b.transform.getTranslation(tmpPos2))
        return bDist.compareTo(aDist)
    }
}