package no.njoh.pulseengine.core.graphics.api.world.views

import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.world.LocalShadowAtlas
import no.njoh.pulseengine.core.graphics.api.world.PreparedRenderPass
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderItem
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderScene
import no.njoh.pulseengine.core.shared.primitives.DynamicList

class WorldLocalShadowRenderView(
    override val viewId: Int,
    private val atlas: LocalShadowAtlas
) : WorldRenderView {

    private val facePasses   = DynamicList<FacePass>(16)
    private val faceFrustums = DynamicList<Frustum>(16)
    private val allItems     = DynamicList<WorldRenderItem>(1024)
    private val faceItems    = DynamicList<WorldRenderItem>(1024)

    var facePassCount = 0
        private set

    override fun update(scene: WorldRenderScene, builder: WorldRenderCommandBuilder)
    {
        val faceCount = atlas.getFaceCount()
        val updateFaceCount = atlas.getUpdateFaceCount()
        if (faceCount == 0 || updateFaceCount == 0)
            return

        allItems.clear()
        allItems += scene.opaqueItems
        allItems += scene.maskedItems

        while (faceFrustums.size < faceCount)
            faceFrustums += Frustum()

        val facesPerPass = if (builder.gpuCullingSupported) LocalShadowAtlas.POINT_FACE_COUNT else 1
        var firstUpdateFace = 0

        while (firstUpdateFace < updateFaceCount)
        {
            while (facePasses.size <= facePassCount)
                facePasses += FacePass()

            val pass = facePasses[facePassCount]
            pass.faceCount = 0

            while (pass.faceCount < facesPerPass && firstUpdateFace < updateFaceCount)
            {
                val faceIndex = atlas.getUpdateFaceIndex(firstUpdateFace++)
                if (faceIndex !in 0 until faceCount)
                    continue

                pass.faceIndices[pass.faceCount++] = faceIndex
                faceFrustums[faceIndex].setForViewProjection(atlas.getFace(faceIndex).viewProjection)
            }

            if (pass.faceCount == 0)
                continue

            for (i in 0 until pass.faceCount)
                pass.frustumPlaneSets[i] = faceFrustums[pass.faceIndices[i]].planeSet

            pass.preparedPass = builder.prepareCullPass(pass.frustumPlaneSets, pass.faceCount)
            {
                faceItems.clear()
                allItems.forEach { if (it.intersectsAnyFace(pass)) faceItems += it }

                pass.bucket.fill(faceItems, requiredView = viewId)
            }

            facePassCount++
        }
    }

    override fun clear()
    {
        facePassCount = 0
        allItems.clear()
        facePasses.forEach { it.clear() }
    }

    fun getFacePass(faceIndex: Int): FacePass?
    {
        for (i in 0 until facePassCount)
        {
            val pass = facePasses[i]
            if (pass.containsFace(faceIndex)) return pass
        }
        return null
    }

    class FacePass
    {
        val bucket = WorldRenderBucket()
        val faceIndices = IntArray(LocalShadowAtlas.POINT_FACE_COUNT)
        val frustumPlaneSets = Array(LocalShadowAtlas.POINT_FACE_COUNT) { FrustumPlaneSet.ofCapacity(0) }
        var preparedPass = PreparedRenderPass.EMPTY
        var faceCount = 0

        fun containsFace(faceIndex: Int): Boolean
        {
            for (i in 0 until faceCount)
            {
                if (faceIndices[i] == faceIndex) return true
            }
            return false
        }

        fun cullViewIndexOf(faceIndex: Int): Int
        {
            for (i in 0 until faceCount)
            {
                if (faceIndices[i] == faceIndex) return i
            }
            return 0
        }

        fun clear()
        {
            bucket.clear()
            preparedPass = PreparedRenderPass.EMPTY
            faceCount = 0
        }
    }

    private fun WorldRenderItem.intersectsAnyFace(pass: FacePass): Boolean
    {
        val bounds = cullingBounds ?: return true
        for (i in 0 until pass.faceCount)
        {
            val faceIndex = pass.faceIndices[i]
            if (faceFrustums[faceIndex].planeSet.intersectsAabb(bounds, transform)) return true
        }
        return false
    }
}