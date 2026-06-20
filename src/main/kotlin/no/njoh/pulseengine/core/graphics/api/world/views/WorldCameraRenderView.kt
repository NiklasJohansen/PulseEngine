package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.TransparencyMode
import no.njoh.pulseengine.core.graphics.api.TransparencyMode.SORTED_BLEND
import no.njoh.pulseengine.core.graphics.api.TransparencyMode.WEIGHTED_BLENDED_OIT
import no.njoh.pulseengine.core.graphics.api.world.CameraRenderState
import no.njoh.pulseengine.core.graphics.api.world.PreparedRenderPass
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.graphics.api.world.views.WorldVisibility.CAMERA
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Vector3f

class WorldCameraRenderView(
    viewId: Int,
    var transparencyMode: TransparencyMode = SORTED_BLEND
) : WorldRenderView(viewId, visibilityMask = CAMERA), CameraRenderStateProvider {

    val opaqueBucket  = WorldRenderBucket()
    val maskedBucket  = WorldRenderBucket()
    val blendedBucket = WorldRenderBucket()

    var preparedPass: PreparedRenderPass = PreparedRenderPass.EMPTY
        private set

    override val cameraStates = DynamicList<CameraRenderState>()
    override val shadowReferencePosition = Vector3f()

    private val cullingFrustum = Frustum()
    private val cameraSeparation = Vector3f()
    private val tmpPos1 = Vector3f()
    private val tmpPos2 = Vector3f()
    private val freeStates = DynamicList<CameraRenderState>()

    override fun beginFrame()
    {
        preparedPass = PreparedRenderPass.EMPTY
        freeStates += cameraStates
        cameraStates.clear()
        opaqueBucket.clear()
        maskedBucket.clear()
        blendedBucket.clear()
    }

    override fun prepare(scene: WorldRenderScene, builder: WorldRenderCommandBuilder)
    {
        if (cameraStates.isEmpty()) return

        preparedPass = builder.prepareCullPass(arrayOf(cullingFrustum.planeSet))
        {
            opaqueBucket.fill(scene.opaqueItems, requiredVisibility = visibilityMask)

            maskedBucket.fill(scene.maskedItems, requiredVisibility = visibilityMask)

            when (transparencyMode)
            {
                SORTED_BLEND -> blendedBucket.fill(
                    from = scene.blendedItems,
                    sortFunc = ::sortedBlendComparator,
                    preserveDrawOrder = true,
                    requiredVisibility = visibilityMask
                )
                WEIGHTED_BLENDED_OIT -> blendedBucket.fill(
                    from = scene.blendedItems,
                    requiredVisibility = visibilityMask
                )
            }
        }
    }

    fun getCameraState(camera: Camera): CameraRenderState? = cameraStates.firstOrNull { it.camera === camera }

    fun addCameraStateFor(camera: Camera, screenWidth: Int = 1, screenHeight: Int = 1): CameraRenderState
    {
        val state = cameraStates.firstOrNull { it.camera === camera } 
            ?: run { (freeStates.removeLastOrNull() ?: CameraRenderState(camera)).also { cameraStates += it } }

        state.setForCamera(camera, screenWidth, screenHeight)
        updateFamilyFrustumAndPosition()
        return state
    }

    private fun updateFamilyFrustumAndPosition()
    {
        when (cameraStates.size)
        {
            0 -> return
            1 ->
            {
                cullingFrustum.setForViewProjection(cameraStates[0].viewProjectionMatrix)
                shadowReferencePosition.set(cameraStates[0].cameraPosition)
            }
            else ->
            {
                val first = cameraStates[0]
                val second = cameraStates[1]
                val secondIsRight = cameraSeparation
                    .set(second.cameraPosition)
                    .sub(first.cameraPosition)
                    .dot(first.cameraRight) >= 0f
                val left = if (secondIsRight) first else second
                val right = if (secondIsRight) second else first

                cullingFrustum.setForSideBySideViewProjections(left.viewProjectionMatrix, right.viewProjectionMatrix)
                shadowReferencePosition.set(first.cameraPosition).add(second.cameraPosition).mul(0.5f)
            }
        }
    }

    private fun sortedBlendComparator(a: WorldRenderItem, b: WorldRenderItem): Int
    {
        val aDist = shadowReferencePosition.distanceSquared(a.transform.getTranslation(tmpPos1))
        val bDist = shadowReferencePosition.distanceSquared(b.transform.getTranslation(tmpPos2))
        return bDist.compareTo(aDist)
    }
}