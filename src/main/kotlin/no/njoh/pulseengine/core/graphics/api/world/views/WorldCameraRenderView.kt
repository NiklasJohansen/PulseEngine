package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import org.joml.Vector3f

class WorldCameraRenderView(override val viewId: Int) : WorldRenderView 
{
    val opaqueBucket  = WorldRenderBucket()
    val maskedBucket  = WorldRenderBucket()
    val blendedBucket = WorldRenderBucket()

    private val frustum = Frustum()
    private val camPos  = Vector3f()
    private val tmpPos1 = Vector3f()
    private val tmpPos2 = Vector3f()

    fun setForCamera(camera: Camera)
    {
        camera.invViewMatrix.getTranslation(camPos)
        frustum.setForCamera(camera)
    }

    override fun update(scene: WorldRenderScene, builder: WorldRenderCommandBuilder)
    {
        builder.beginCullPass()

        opaqueBucket.fill(
            builder = builder,
            items = scene.opaqueItems,
            frustum = frustum,
            requiredView = viewId
        )

        maskedBucket.fill(
            builder = builder,
            items = scene.maskedItems,
            frustum = frustum,
            requiredView = viewId
        )

        blendedBucket.fill(
            builder = builder,
            items = scene.blendedItems,
            frustum = frustum,
            sortFunc = ::compareForTransparency,
            preserveItemOrder = true,
            requiredView = viewId
        )

        builder.submitCullPass(
            buckets = arrayOf(opaqueBucket, maskedBucket, blendedBucket),
            frustumPlaneSets = arrayOf(frustum.planeSet)
        )
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