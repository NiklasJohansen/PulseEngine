package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.PreparedRenderPass
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class WorldShadowRenderView(override val viewId: Int) : WorldRenderView
{
    val bucket = WorldRenderBucket()

    var preparedPass: PreparedRenderPass = PreparedRenderPass.EMPTY
        private set

    private var cascadeFrustumPlaneSets = arrayOf<FrustumPlaneSet>()
    private val allItems = DynamicList<WorldRenderItem>(1024)

    fun prepare(cascadeFrustumPlaneSets: Array<FrustumPlaneSet>)
    {
        this.cascadeFrustumPlaneSets = cascadeFrustumPlaneSets
    }

    fun getCascadeCullView(cascade: Int) = preparedPass.cullView(cascade)

    override fun update(scene: WorldRenderScene, builder: WorldRenderCommandBuilder)
    {
        allItems.clear()
        allItems += scene.opaqueItems
        allItems += scene.maskedItems

        preparedPass = builder.prepareCullPass(cascadeFrustumPlaneSets)
        {
            bucket.fill(allItems, requiredView = viewId)
        }
    }

    override fun clear()
    {
        preparedPass = PreparedRenderPass.EMPTY
        bucket.clear()
    }
}