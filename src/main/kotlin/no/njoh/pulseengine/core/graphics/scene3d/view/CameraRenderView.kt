package no.njoh.pulseengine.core.graphics.scene3d.view

import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.scene3d.draw.TransparencyMode
import no.njoh.pulseengine.core.graphics.scene3d.draw.TransparencyMode.SORTED_BLEND
import no.njoh.pulseengine.core.graphics.scene3d.draw.TransparencyMode.WEIGHTED_BLENDED_OIT
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawPayload
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawPayload.EmptyDrawPayload
import no.njoh.pulseengine.core.graphics.scene3d.draw.RenderBucket
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawCommandBuilder
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.CAMERA
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Vector3f

class CameraRenderView(
    renderPassMask: RenderPassMask = CAMERA,
    var transparencyMode: TransparencyMode = SORTED_BLEND
) : RenderView(renderPassMask), CameraRenderStateProvider {

    val opaqueBucket  = RenderBucket()
    val maskedBucket  = RenderBucket()
    val blendedBucket = RenderBucket()

    var drawPayload: DrawPayload = EmptyDrawPayload
        private set

    override val cameraStates = DynamicList<CameraRenderState>()
    override val shadowReferencePosition = Vector3f()

    private val cullingFrustum = Frustum()
    private val cullingFrustumPlaneSets = arrayOf(cullingFrustum.planeSet)
    private val cameraSeparation = Vector3f()
    private val tmpPos1 = Vector3f()
    private val tmpPos2 = Vector3f()
    private val freeStates = DynamicList<CameraRenderState>()
    private val sortedBlendSort: (RenderItem, RenderItem) -> Int = ::sortedBlendComparator

    override fun beginFrame()
    {
        drawPayload = EmptyDrawPayload
        freeStates += cameraStates
        cameraStates.clear()
        opaqueBucket.clear()
        maskedBucket.clear()
        blendedBucket.clear()
    }

    override fun prepare(scene: RenderScene, builder: DrawCommandBuilder)
    {
        if (cameraStates.isEmpty()) return

        drawPayload = builder.prepareDrawPayload(cullingFrustumPlaneSets)
        {
            opaqueBucket.fill(scene.opaqueItems, renderPassMask)

            maskedBucket.fill(scene.maskedItems, renderPassMask)

            when (transparencyMode)
            {
                SORTED_BLEND -> blendedBucket.fill(
                    from = scene.blendedItems,
                    renderPassMask = renderPassMask,
                    sortFunc = sortedBlendSort,
                    preserveDrawOrder = true
                )
                WEIGHTED_BLENDED_OIT -> blendedBucket.fill(
                    from = scene.blendedItems,
                    renderPassMask = renderPassMask
                )
            }
        }
    }

    fun getCameraState(camera: Camera): CameraRenderState? = cameraStates.firstOrNull { it.camera === camera }

    fun addCameraStateFor(camera: Camera, screenWidth: Int = 1, screenHeight: Int = 1): CameraRenderState
    {
        val existingState = cameraStates.firstOrNull { it.camera === camera }
        require(existingState != null || cameraStates.size < MAX_CAMERA_STATES) { "Camera render view supports at most $MAX_CAMERA_STATES cameras (mono or stereo)" }

        val state = existingState ?: (freeStates.removeLastOrNull() ?: CameraRenderState(camera)).also { cameraStates += it }
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

    private fun sortedBlendComparator(a: RenderItem, b: RenderItem): Int
    {
        val aDist = shadowReferencePosition.distanceSquared(a.transform.getTranslation(tmpPos1))
        val bDist = shadowReferencePosition.distanceSquared(b.transform.getTranslation(tmpPos2))
        return bDist.compareTo(aDist)
    }

    companion object
    {
        private const val MAX_CAMERA_STATES = 2
    }
}