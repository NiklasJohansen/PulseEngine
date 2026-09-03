package no.njoh.pulseengine.core.graphics.scene3d.view

import no.njoh.pulseengine.core.graphics.scene3d.view.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawPayload
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawPayload.EmptyDrawPayload
import no.njoh.pulseengine.core.graphics.scene3d.draw.RenderBucket
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawCommandBuilder
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.GLOBAL_SHADOW

class GlobalShadowRenderView : RenderView(GLOBAL_SHADOW)
{
    val opaqueBucket = RenderBucket()
    val maskedBucket = RenderBucket()

    var drawPayload: DrawPayload = EmptyDrawPayload
        private set

    private var cascadeFrustumPlaneSets = arrayOf<FrustumPlaneSet>()

    override fun beginFrame()
    {
        drawPayload = EmptyDrawPayload
        opaqueBucket.clear()
        maskedBucket.clear()
    }

    override fun prepare(scene: RenderScene, builder: DrawCommandBuilder)
    {
        drawPayload = builder.prepareDrawPayload(cascadeFrustumPlaneSets)
        {
            opaqueBucket.fill(scene.opaqueItems, renderPassMask)
            maskedBucket.fill(scene.maskedItems, renderPassMask)
        }
    }

    fun setFrustumPlaneSets(cascadeFrustumPlaneSets: Array<FrustumPlaneSet>)
    {
        this.cascadeFrustumPlaneSets = cascadeFrustumPlaneSets
    }
}