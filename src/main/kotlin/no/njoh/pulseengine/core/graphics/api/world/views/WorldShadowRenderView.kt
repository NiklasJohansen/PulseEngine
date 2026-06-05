package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f

class WorldShadowRenderView(override val viewId: Int) : WorldRenderView
{
    val bucket = WorldRenderBucket()

    private val shadowFrustum = Frustum()
    private var cascadeFrustumPlaneSets = arrayOf<FrustumPlaneSet>()
    private val allItems = DynamicList<WorldRenderItem>(1024)

    fun prepare(
        shadowCullingMatrix: Matrix4f,
        cascadeFrustumPlaneSets: Array<FrustumPlaneSet>
    ) {
        this.shadowFrustum.setForViewProjection(shadowCullingMatrix)
        this.cascadeFrustumPlaneSets = cascadeFrustumPlaneSets
    }

    override fun update(scene: WorldRenderScene, builder: WorldRenderCommandBuilder)
    {
        builder.beginCullPass()

        allItems.clear()
        allItems += scene.opaqueItems
        allItems += scene.maskedItems

        bucket.fill(
            builder = builder,
            items = allItems,
            frustum = shadowFrustum,
            requiredView = viewId
        )

        builder.submitCullPass(arrayOf(bucket), cascadeFrustumPlaneSets)
    }

    override fun clear()
    {
        bucket.clear()
    }
}