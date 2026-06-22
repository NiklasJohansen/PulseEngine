package no.njoh.pulseengine.core.graphics.scene3d.view

import no.njoh.pulseengine.core.graphics.scene3d.view.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawPayload
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawPayload.EmptyDrawPayload
import no.njoh.pulseengine.core.graphics.scene3d.draw.RenderBucket
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawCommandBuilder
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderVisibility.GLOBAL_SHADOW
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class GlobalShadowRenderView(viewId: Int) : RenderView(viewId, visibilityMask = GLOBAL_SHADOW)
{
    val bucket = RenderBucket()

    var drawPayload: DrawPayload = EmptyDrawPayload
        private set

    private var cascadeFrustumPlaneSets = arrayOf<FrustumPlaneSet>()
    private val allItems = DynamicList<RenderItem>(1024)

    override fun beginFrame()
    {
        drawPayload = EmptyDrawPayload
        bucket.clear()
    }

    override fun prepare(scene: RenderScene, builder: DrawCommandBuilder)
    {
        allItems.clear()
        allItems += scene.opaqueItems
        allItems += scene.maskedItems

        drawPayload = builder.prepareDrawPayload(cascadeFrustumPlaneSets)
        {
            bucket.fill(allItems, requiredVisibility = visibilityMask)
        }
    }

    fun setFrustumPlaneSets(cascadeFrustumPlaneSets: Array<FrustumPlaneSet>)
    {
        this.cascadeFrustumPlaneSets = cascadeFrustumPlaneSets
    }
}