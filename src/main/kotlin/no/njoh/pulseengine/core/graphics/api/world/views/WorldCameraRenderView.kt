package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.CameraProjectionType.PERSPECTIVE
import no.njoh.pulseengine.core.graphics.api.DefaultCamera
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderDrawBuffer
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import org.joml.Vector3f

class WorldCameraRenderView(override val viewId: Int) : WorldRenderView 
{
    val opaqueBucket  = WorldRenderBucket()
    val maskedBucket  = WorldRenderBucket()
    val blendedBucket = WorldRenderBucket()

    private var camera: Camera = DefaultCamera(PERSPECTIVE)
    private val frustum = Frustum()
    private val camPos  = Vector3f()
    private val tmpPos1 = Vector3f()
    private val tmpPos2 = Vector3f()

    fun setCamera(camera: Camera)
    {
        this.camera = camera
    }

    override fun update(scene: WorldRenderScene, drawBuffer: WorldRenderDrawBuffer)
    {
        camera.invViewMatrix.getTranslation(camPos)
        frustum.setForCamera(camera)

        drawBuffer.beginCullPass()

        opaqueBucket.fill(
            drawBuffer = drawBuffer,
            items = scene.opaqueItems,
            frustum = frustum,
            requiredView = viewId,
            commandStartIndex = 0
        )

        maskedBucket.fill(
            drawBuffer = drawBuffer,
            items = scene.maskedItems,
            frustum = frustum,
            requiredView = viewId,
            commandStartIndex = opaqueBucket.size
        )

        blendedBucket.fill(
            drawBuffer = drawBuffer,
            items = scene.blendedItems,
            frustum = frustum,
            sortFunc = ::compareForTransparency,
            preserveItemOrder = true,
            requiredView = viewId,
            commandStartIndex = opaqueBucket.size + maskedBucket.size
        )

        drawBuffer.submitCullPass(listOf(opaqueBucket, maskedBucket, blendedBucket), frustum)
    }

    override fun clear()
    {
        opaqueBucket.clear()
        maskedBucket.clear()
        blendedBucket.clear()
    }

    private fun compareForTransparency(a: WorldRenderItem, b: WorldRenderItem): Int
    {
        val aDist = camPos.distanceSquared(a.transform.getTranslation(tmpPos1))
        val bDist = camPos.distanceSquared(b.transform.getTranslation(tmpPos2))
        return bDist.compareTo(aDist)
    }
}