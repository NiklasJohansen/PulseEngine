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
import no.njoh.pulseengine.core.graphics.api.world.ClusteredLightGrid
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3fc

class WorldCameraRenderView(
    override val viewId: Int,
    var transparencyMode: TransparencyMode = SORTED_BLEND
) : WorldRenderView {

    val opaqueBucket  = WorldRenderBucket()
    val maskedBucket  = WorldRenderBucket()
    val blendedBucket = WorldRenderBucket()
    val clusteredLights = ClusteredLightGrid()

    val frustum = Frustum()
    val viewMatrix = Matrix4f()
    val projectionMatrix = Matrix4f()

    var screenWidth  = 1;     private set
    var screenHeight = 1;     private set
    var nearPlane    = 0.05f; private set
    var farPlane     = 1f;    private set

    var preparedPass: PreparedRenderPass = PreparedRenderPass.EMPTY
        private set

    private val camPos  = Vector3f()
    private val tmpPos1 = Vector3f()
    private val tmpPos2 = Vector3f()

    val cameraPosition: Vector3fc get() = camPos

    fun setForCamera(camera: Camera, screenWidth: Int = 1, screenHeight: Int = 1)
    {
        camera.invViewMatrix.getTranslation(camPos)

        this.frustum.setForCamera(camera)
        this.viewMatrix.set(camera.viewMatrix)
        this.projectionMatrix.set(camera.projectionMatrix)
        this.screenWidth = screenWidth.coerceAtLeast(1)
        this.screenHeight = screenHeight.coerceAtLeast(1)
        this.nearPlane = camera.nearPlane
        this.farPlane = camera.farPlane
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

    fun destroy()
    {
        clusteredLights.destroy()
    }

    private fun sortedBlendComparator(a: WorldRenderItem, b: WorldRenderItem): Int
    {
        val aDist = camPos.distanceSquared(a.transform.getTranslation(tmpPos1))
        val bDist = camPos.distanceSquared(b.transform.getTranslation(tmpPos2))
        return bDist.compareTo(aDist)
    }
}