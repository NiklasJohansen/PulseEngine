package no.njoh.pulseengine.core.graphics.scene3d.view

import gnu.trove.list.array.TIntArrayList
import no.njoh.pulseengine.core.graphics.scene3d.view.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.scene3d.shadow.LocalShadowAtlas
import no.njoh.pulseengine.core.graphics.scene3d.shadow.LocalShadowAtlas.Companion.POINT_LIGHT_SHADOW_FACE_COUNT
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawPayload
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawPayload.EmptyDrawPayload
import no.njoh.pulseengine.core.graphics.scene3d.draw.RenderBucket
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawCommandBuilder
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.LOCAL_SHADOW
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class LocalShadowRenderView(private val atlas: LocalShadowAtlas) : RenderView(LOCAL_SHADOW)
{
    private val shadowFacePasses   = DynamicList<ShadowFaceRenderPass>(16)
    private val shadowFaceFrustums = DynamicList<Frustum>(16)
    private val shadowFaceItems    = DynamicList<RenderItem>(1024)

    var shadowFacePassCount = 0
        private set

    override fun beginFrame()
    {
        shadowFacePasses.forEach { it.clear() }
        shadowFacePassCount = 0
    }

    override fun prepare(scene: RenderScene, builder: DrawCommandBuilder)
    {
        val shadowFaceCount = atlas.getActiveShadowFaces().size
        val numShadowFacesToRender = atlas.getNumberOfShadowFacesToRender()
        if (shadowFaceCount == 0 || numShadowFacesToRender == 0)
            return

        val shadowFacesPerPass = if (builder.gpuCullingSupported) POINT_LIGHT_SHADOW_FACE_COUNT else 1

        var i = 0
        while (i < numShadowFacesToRender)
        {
            val pass = shadowFacePasses.getOrAdd(shadowFacePassCount) { ShadowFaceRenderPass() }

            while (pass.getNumShadowFacesToRender() < shadowFacesPerPass && i < numShadowFacesToRender)
            {
                val faceIndex = atlas.getShadowFaceIndexToRender(i++)
                val viewProjection = atlas.getShadowFace(faceIndex).viewProjection
                val planeSet = shadowFaceFrustums.getOrAdd(faceIndex) { Frustum() }.setForViewProjection(viewProjection).planeSet
                pass.addShadowFaceToRender(faceIndex, planeSet)
            }

            if (pass.getNumShadowFacesToRender() == 0) continue

            pass.drawPayload = builder.prepareDrawPayload(pass.frustumPlaneSets, pass.getNumShadowFacesToRender())
            {
                shadowFaceItems.clear()
                scene.opaqueItems.forEach { if (it.intersectsAnyFace(pass)) shadowFaceItems += it }
                scene.maskedItems.forEach { if (it.intersectsAnyFace(pass)) shadowFaceItems += it }

                pass.bucket.fill(shadowFaceItems, renderPassMask)
            }

            shadowFacePassCount++
        }
    }

    fun getShadowFaceRenderPass(faceIndex: Int): ShadowFaceRenderPass?
    {
        for (i in 0 until shadowFacePassCount)
        {
            val pass = shadowFacePasses[i]
            if (pass.containsShadowFace(faceIndex)) return pass
        }
        return null
    }

    private fun RenderItem.intersectsAnyFace(pass: ShadowFaceRenderPass): Boolean
    {
        val bounds = cullingBounds ?: return true
        for (i in 0 until pass.getNumShadowFacesToRender())
        {
            if (pass.frustumPlaneSets[i].intersectsAabb(bounds, transform)) return true
        }
        return false
    }

    class ShadowFaceRenderPass
    {
        val bucket = RenderBucket()
        val frustumPlaneSets = Array(POINT_LIGHT_SHADOW_FACE_COUNT) { FrustumPlaneSet.ofCapacity(0) }
        var drawPayload: DrawPayload = EmptyDrawPayload

        private val shadowFaceIndices = TIntArrayList(POINT_LIGHT_SHADOW_FACE_COUNT)

        fun clear()
        {
            bucket.clear()
            drawPayload = EmptyDrawPayload
            shadowFaceIndices.resetQuick()
        }

        fun addShadowFaceToRender(shadowFaceIndex: Int, frustumPlaneSet: FrustumPlaneSet)
        {
            frustumPlaneSets[shadowFaceIndices.size()] = frustumPlaneSet
            shadowFaceIndices.add(shadowFaceIndex)
        }

        fun getNumShadowFacesToRender(): Int = shadowFaceIndices.size()

        fun containsShadowFace(shadowFaceIndex: Int): Boolean = shadowFaceIndices.contains(shadowFaceIndex)

        fun cullViewIndexOf(shadowFaceIndex: Int): Int = shadowFaceIndices.indexOf(shadowFaceIndex)
    }
}