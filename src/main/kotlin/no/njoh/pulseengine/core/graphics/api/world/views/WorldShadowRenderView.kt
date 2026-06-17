package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.PreparedRenderPass
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.graphics.api.world.views.WorldVisibility.GLOBAL_SHADOW
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class WorldShadowRenderView(
    viewId: Int,
) : WorldRenderView(viewId, visibilityMask = GLOBAL_SHADOW) {

    val bucket = WorldRenderBucket()

    var preparedPass: PreparedRenderPass = PreparedRenderPass.EMPTY
        private set

    private var cascadeFrustumPlaneSets = arrayOf<FrustumPlaneSet>()
    private val allItems = DynamicList<WorldRenderItem>(1024)

    override fun beginFrame()
    {
        preparedPass = PreparedRenderPass.EMPTY
        bucket.clear()
    }

    override fun prepare(scene: WorldRenderScene, builder: WorldRenderCommandBuilder)
    {
        allItems.clear()
        allItems += scene.opaqueItems
        allItems += scene.maskedItems

        preparedPass = builder.prepareCullPass(cascadeFrustumPlaneSets)
        {
            bucket.fill(allItems, requiredVisibility = visibilityMask)
        }
    }

    fun setFrustumPlaneSets(cascadeFrustumPlaneSets: Array<FrustumPlaneSet>)
    {
        this.cascadeFrustumPlaneSets = cascadeFrustumPlaneSets
    }
}