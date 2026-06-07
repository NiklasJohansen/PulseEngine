package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.PreparedRenderPass
import no.njoh.pulseengine.core.graphics.api.TransparencyMode
import no.njoh.pulseengine.core.graphics.api.TransparencyMode.SORTED_BLEND
import no.njoh.pulseengine.core.graphics.api.TransparencyMode.WEIGHTED_BLENDED_OIT
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import org.joml.Vector3f

class WorldCameraRenderView(
    override val viewId: Int,
    var transparencyMode: TransparencyMode = SORTED_BLEND
) : WorldRenderView {

    val opaqueBucket  = WorldRenderBucket()
    val maskedBucket  = WorldRenderBucket()
    val blendedBucket = WorldRenderBucket()

    var preparedPass: PreparedRenderPass = PreparedRenderPass.EMPTY
        private set

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
        preparedPass = builder.prepareCullPass(arrayOf(frustum.planeSet))
        {
            opaqueBucket.fill(scene.opaqueItems, requiredView = viewId)

            maskedBucket.fill(scene.maskedItems, requiredView = viewId)

            when (transparencyMode)
            {
                SORTED_BLEND -> blendedBucket.fill(
                    from = scene.blendedItems,
                    sortFunc = ::sortedBlendComparator,
                    preserveDrawOrder = true,
                    requiredView = viewId
                )
                WEIGHTED_BLENDED_OIT -> blendedBucket.fill(
                    from = scene.blendedItems,
                    requiredView = viewId
                )
            }
        }
    }

    override fun clear()
    {
        preparedPass = PreparedRenderPass.EMPTY
        opaqueBucket.clear()
        maskedBucket.clear()
        blendedBucket.clear()
    }

    private fun sortedBlendComparator(a: WorldRenderItem, b: WorldRenderItem): Int
    {
        val aDist = camPos.distanceSquared(a.transform.getTranslation(tmpPos1))
        val bDist = camPos.distanceSquared(b.transform.getTranslation(tmpPos2))
        return bDist.compareTo(aDist)
    }
}