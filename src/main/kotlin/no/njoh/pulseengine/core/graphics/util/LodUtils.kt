package no.njoh.pulseengine.core.graphics.util

import gnu.trove.map.hash.TLongObjectHashMap
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.Aabb
import no.njoh.pulseengine.core.asset.types.Model.LodLevel
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderState
import no.njoh.pulseengine.core.shared.utils.getOrPut
import org.joml.Matrix4f
import kotlin.math.abs
import kotlin.math.max

object LodUtils
{
    const val LOD_STATE_RETENTION_FRAMES = 120

    private val lodModelViewProjection = ThreadLocal.withInitial { Matrix4f() }
    private val lodModelStates = TLongObjectHashMap<LodModelState>()

    fun pruneStaleLodStates(frameNumber: Int)
    {
        lodModelStates.retainEntries { _, value -> frameNumber - value.lastFrameSeen <= LOD_STATE_RETENTION_FRAMES }
    }

    fun getLodLevel(model: Model, transform: Matrix4f, pixelHeightThresholds: IntArray?, hysteresis: Float, lodKey: Long, cameraState: LodCameraState): Int
    {
        val lodLevels = model.lodLevels
        if (lodLevels.isEmpty() || pixelHeightThresholds == null || pixelHeightThresholds.isEmpty() || model.hasBones)
            return 0

        val pixelHeight = getProjectedPixelHeight(model.localBounds, transform, cameraState)

        if (lodKey == 0L)
        {
            var level = 0
            for (i in 0 until pixelHeightThresholds.size)
            {
                if (pixelHeight < pixelHeightThresholds[i]) level = i + 1 else break
            }
            return getNearestLodLevel(lodLevels, level)
        }

        val state = lodModelStates.getOrPut(lodKey) { LodModelState(getNearestLodLevel(lodLevels, 0), cameraState.frameNumber) }
        val level = selectLodLevelWithHysteresis(pixelHeight, state.level, pixelHeightThresholds, hysteresis)

        state.level = getNearestLodLevel(lodLevels, level)
        state.lastFrameSeen = cameraState.frameNumber

        return state.level
    }

    fun getNearestLodLevel(lodLevels: List<LodLevel>, requestedLevel: Int): Int
    {
        if (lodLevels.isEmpty()) return 0

        var nearestLevel = lodLevels[0].level
        var nearestDistance = abs(requestedLevel - nearestLevel)

        for (i in 1 until lodLevels.size)
        {
            val level = lodLevels[i].level
            val distance = abs(requestedLevel - level)
            if (distance < nearestDistance || (distance == nearestDistance && level < nearestLevel))
            {
                nearestLevel = level
                nearestDistance = distance
            }
        }

        return nearestLevel
    }

    private fun selectLodLevelWithHysteresis(pixelHeight: Float, previousLevel: Int, pixelHeightThresholds: IntArray, lodHysteresis: Float): Int
    {
        val maxLevel = pixelHeightThresholds.size
        var level = previousLevel.coerceIn(0, maxLevel)
        val hysteresis = lodHysteresis.coerceIn(0f, 0.9f)

        while (level > 0)
        {
            val threshold = pixelHeightThresholds[level - 1]
            if (pixelHeight >= threshold * (1f + hysteresis)) level-- else break
        }

        while (level < maxLevel)
        {
            val threshold = pixelHeightThresholds[level]
            if (pixelHeight < threshold * (1f - hysteresis)) level++ else break
        }

        return level
    }

    private fun getProjectedPixelHeight(bounds: Aabb?, transform: Matrix4f, state: LodCameraState?): Float
    {
        if (bounds == null || state == null || !state.isValid)
            return Float.POSITIVE_INFINITY

        val mvp = lodModelViewProjection.get().set(state.viewProjectionMatrix).mul(transform)
        val nearDepth = max(state.nearPlane, 0.0001f)
        val m01 = mvp.m01()
        val m11 = mvp.m11()
        val m21 = mvp.m21()
        val m31 = mvp.m31()
        val m03 = mvp.m03()
        val m13 = mvp.m13()
        val m23 = mvp.m23()
        val m33 = mvp.m33()
        var minNdcY = Float.POSITIVE_INFINITY
        var maxNdcY = Float.NEGATIVE_INFINITY

        for (corner in 0 until 8)
        {
            val x = if (corner and 1 == 0) bounds.xMin else bounds.xMax
            val y = if (corner and 2 == 0) bounds.yMin else bounds.yMax
            val z = if (corner and 4 == 0) bounds.zMin else bounds.zMax
            val clipY = m01 * x + m11 * y + m21 * z + m31
            val clipW = m03 * x + m13 * y + m23 * z + m33

            if (state.isPerspective && clipW <= nearDepth)
                return Float.POSITIVE_INFINITY

            val ndcY = clipY / clipW
            if (ndcY < minNdcY) minNdcY = ndcY
            if (ndcY > maxNdcY) maxNdcY = ndcY
        }

        return (maxNdcY - minNdcY) * 0.5f * state.screenHeight
    }
}

data class LodModelState(
    var level: Int,
    var lastFrameSeen: Int
)

class LodCameraState()
{
    val viewProjectionMatrix = Matrix4f()
    var screenHeight = 1080
    var nearPlane = 0.1f
    var frameNumber = 0
    var isPerspective = true
    var isValid = false

    fun set(state: CameraRenderState, frameNum: Int)
    {
        viewProjectionMatrix.set(state.viewProjectionMatrix)
        screenHeight = state.screenHeight
        nearPlane = state.nearPlane
        frameNumber = frameNum
        isPerspective = abs(state.projectionMatrix.m33()) < 0.0001f
        isValid = true
    }

    fun invalidate()
    {
        isValid = false
    }
}
