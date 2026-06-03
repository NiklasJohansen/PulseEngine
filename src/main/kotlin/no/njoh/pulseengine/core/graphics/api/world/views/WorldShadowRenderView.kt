package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderDrawBuffer
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f

class WorldShadowRenderView(override val viewId: Int) : WorldRenderView
{
    val bucket = WorldRenderBucket()

    private val shadowFrustum = Frustum()
    private val allItems = DynamicList<WorldRenderItem>(1024)
    private var shadowCullingMatrix = Matrix4f()
    private var cascadeFrustumPlaneSets = arrayOf<Frustum.FrustumPlaneSet>()

    fun prepare(
        shadowCullingMatrix: Matrix4f,
        cascadeFrustumPlaneSets: Array<Frustum.FrustumPlaneSet>
    ) {
        this.shadowCullingMatrix = shadowCullingMatrix
        this.cascadeFrustumPlaneSets = cascadeFrustumPlaneSets
    }

    override fun update(scene: WorldRenderScene, drawBuffer: WorldRenderDrawBuffer)
    {
        shadowFrustum.setForViewProjection(shadowCullingMatrix)
        allItems.clear()
        allItems += scene.opaqueItems
        allItems += scene.maskedItems

        drawBuffer.beginCullPass()

        bucket.fill(
            drawBuffer = drawBuffer,
            items = allItems,
            frustum = shadowFrustum,
            requiredView = viewId,
            commandStartIndex = 0
        )

        drawBuffer.submitCullPass(bucket, cascadeFrustumPlaneSets)
    }

    override fun clear()
    {
        bucket.clear()
    }
}