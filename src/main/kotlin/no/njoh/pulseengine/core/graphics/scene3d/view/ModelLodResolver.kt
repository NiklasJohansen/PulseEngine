package no.njoh.pulseengine.core.graphics.scene3d.view

import gnu.trove.list.array.TFloatArrayList
import gnu.trove.map.hash.TLongLongHashMap
import no.njoh.pulseengine.core.graphics.scene3d.submission.ModelItem

/**
 * Selects one LOD per model using the nearest camera requested by any active render view.
 */
class ModelLodResolver
{
    private val cameraPositions       = TFloatArrayList(4 * 3)
    private var frameNumber           = 0
    private var expectedLodStateCount = 0
    private var lodStates             = null as TLongLongHashMap?

    fun beginFrame(views: Collection<RenderView>, frameNumber: Int, expectedLodStateCount: Int)
    {
        this.frameNumber = frameNumber
        this.expectedLodStateCount = expectedLodStateCount
        this.cameraPositions.resetQuick()

        views.forEach { view ->
            if (view.lastFrameRequested == frameNumber && view is CameraRenderStateProvider)
            {
                view.cameraStates.forEach() 
                {
                    cameraPositions.add(it.cameraPosition.x)
                    cameraPositions.add(it.cameraPosition.y)
                    cameraPositions.add(it.cameraPosition.z)
                }
            }
        }

        if (frameNumber and (LOD_STATE_CLEANUP_INTERVAL - 1) == 0)
        {
            val oldestFrameToKeep = frameNumber - LOD_STATE_RETENTION_FRAMES
            lodStates?.retainEntries { _, packedState -> unpackFrameNumber(packedState) >= oldestFrameToKeep }
        }
    }

    fun resolve(submission: ModelItem): Int
    {
        val squaredThresholds = submission.lodThresholds ?: return 0
        val maxLodLevel = minOf(submission.model.lodLevels.lastIndex, squaredThresholds.size)
        val squaredDistance = getNearestCameraDistanceSquared(submission)
        val lodHysteresis = submission.lodHysteresis
        val lodKey = submission.lodKey

        if (lodKey == 0L || !lodHysteresis.isFinite() || lodHysteresis <= 0f)
            return getLodLevel(squaredDistance, squaredThresholds, maxLodLevel)

        var states = lodStates
        if (states == null)
        {
            val initialCapacity = expectedLodStateCount.coerceAtLeast(DEFAULT_LOD_STATE_CAPACITY)
            states = TLongLongHashMap(initialCapacity)
            lodStates = states
        }

        val packedState = states.get(lodKey)
        val previousLevel = if (packedState == NO_LOD_STATE) 0 else packedState.toInt()
        val level = getLodLevel(squaredDistance, squaredThresholds, maxLodLevel, previousLevel, lodHysteresis)
        states.put(lodKey, packLodState(level, frameNumber))

        return level
    }

    private fun getNearestCameraDistanceSquared(submission: ModelItem): Float
    {
        if (cameraPositions.isEmpty)
            return 0f

        val localCenter = submission.model.localBoundingSphere
        val transform = submission.transform
        val x = localCenter.x
        val y = localCenter.y
        val z = localCenter.z
        val centerAtOrigin = x == 0f && y == 0f && z == 0f
        val xWorld = if (centerAtOrigin) transform.m30() else transform.m00() * x + transform.m10() * y + transform.m20() * z + transform.m30()
        val yWorld = if (centerAtOrigin) transform.m31() else transform.m01() * x + transform.m11() * y + transform.m21() * z + transform.m31()
        val zWorld = if (centerAtOrigin) transform.m32() else transform.m02() * x + transform.m12() * y + transform.m22() * z + transform.m32()

        var nearestDistSquared = Float.POSITIVE_INFINITY

        for (i in 0 until cameraPositions.size() step 3)
        {
            val xDist = xWorld - cameraPositions[i + 0]
            val yDist = yWorld - cameraPositions[i + 1]
            val zDist = zWorld - cameraPositions[i + 2]
            val disSquared = xDist * xDist + yDist * yDist + zDist * zDist
            if (disSquared < nearestDistSquared)
                nearestDistSquared = disSquared
        }

        return nearestDistSquared
    }

    private fun getLodLevel(distanceSquared: Float, squaredThresholds: FloatArray, maxLodLevel: Int): Int
    {
        var level = 0
        while (level < maxLodLevel && distanceSquared > squaredThresholds[level])
            level++

        return level
    }

    private fun getLodLevel(distanceSquared: Float, squaredThresholds: FloatArray, maxLodLevel: Int, previousLevel: Int, lodHysteresis: Float): Int
    {
        val hysteresis = lodHysteresis.coerceIn(0f, 0.9f)
        val finerScale = 1f - hysteresis
        val coarserScale = 1f + hysteresis
        val finerScaleSquared = finerScale * finerScale
        val coarserScaleSquared = coarserScale * coarserScale
        var level = previousLevel.coerceIn(0, maxLodLevel)

        while (level > 0)
        {
            val thresholdSquared = squaredThresholds[level - 1] * finerScaleSquared
            if (distanceSquared <= thresholdSquared) level-- else break
        }

        while (level < maxLodLevel)
        {
            val thresholdSquared = squaredThresholds[level] * coarserScaleSquared
            if (distanceSquared > thresholdSquared) level++ else break
        }

        return level
    }

    companion object
    {
        private const val NO_LOD_STATE = 0L
        private const val DEFAULT_LOD_STATE_CAPACITY = 10
        private const val LOD_STATE_RETENTION_FRAMES = 120
        private const val LOD_STATE_CLEANUP_INTERVAL = 32

        private fun packLodState(level: Int, frameNumber: Int) =
            ((frameNumber.toLong() + 1L) shl Int.SIZE_BITS) or (level.toLong() and 0xFFFF_FFFFL)

        private fun unpackFrameNumber(packedState: Long) = (packedState ushr Int.SIZE_BITS).toInt() - 1
    }
}