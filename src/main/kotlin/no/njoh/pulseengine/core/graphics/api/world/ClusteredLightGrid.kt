package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.world.views.CameraRenderState
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class ClusteredLightGrid
{
    var enabled         = false;              private set
    var gridWidth       = 1;                  private set
    var gridHeight      = 1;                  private set
    var gridDepth       = DEFAULT_GRID_Z;     private set
    var xTileSize       = DEFAULT_TILE_SIZE;  private set
    var yTileSize       = DEFAULT_TILE_SIZE;  private set
    var nearPlane       = 0.05f;              private set
    var farPlane        = 1f;                 private set
    var depthSliceScale = 1f;                 private set
    var clusterCount    = 1;                  private set

    private lateinit var clusterBuffer: StreamingIntBufferObject
    private lateinit var indexBuffer: StreamingIntBufferObject

    private var clusterCounts     = IntArray(0)
    private var clusterOffsets    = IntArray(0)
    private var clusterCursors    = IntArray(0)
    private var lightIndexScratch = IntArray(0)

    private val tmpViewPos       = Vector4f()
    private val tmpClipPos       = Vector4f()
    private var xNdcProjectedMin = 0f
    private var xNdcProjectedMax = 0f
    private var yNdcProjectedMin = 0f
    private var yNdcProjectedMax = 0f
    private var initialized      = false
    private var submitted        = false

    private fun init()
    {
        if (initialized) return

        clusterBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CLUSTER_BUFFER_BINDING, CLUSTER_INTS * 1024, BUFFER_SEGMENTS)
        indexBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CLUSTER_INDEX_BUFFER_BINDING, 4096, BUFFER_SEGMENTS)
        initialized = true
    }
    
    fun buildAndSubmit(state: CameraRenderState, scene: WorldRenderScene)
    {
        init()
        clusterBuffer.clear()
        indexBuffer.clear()

        val lights = scene.localLights
        nearPlane = max(0.01f, state.nearPlane)
        farPlane = max(nearPlane + 0.01f, state.farPlane)
        depthSliceScale = gridDepth.toFloat() / ln(farPlane / nearPlane)

        if (lights.isEmpty())
        {
            clearAndSubmit()
            return
        }

        gridWidth = max(1, (state.screenWidth + xTileSize - 1) / xTileSize)
        gridHeight = max(1, (state.screenHeight + yTileSize - 1) / yTileSize)
        gridDepth = DEFAULT_GRID_Z
        clusterCount = gridWidth * gridHeight * gridDepth

        if (clusterCounts.size < clusterCount)
        {
            clusterCounts  = IntArray(clusterCount)
            clusterOffsets = IntArray(clusterCount)
            clusterCursors = IntArray(clusterCount)
        }
        clusterCounts.fill(0, 0, clusterCount)

        for (lightIndex in 0 until lights.size)
            forEachTouchedCluster(state, lights[lightIndex]) { clusterCounts[it]++ }

        var totalIndexCount = 0
        for (cluster in 0 until clusterCount)
        {
            clusterOffsets[cluster] = totalIndexCount
            clusterCursors[cluster] = totalIndexCount
            totalIndexCount += clusterCounts[cluster]
        }

        val requiredCount = max(1, totalIndexCount)
        if (lightIndexScratch.size < requiredCount)
            lightIndexScratch = IntArray(requiredCount)

        for (lightIndex in 0 until lights.size)
        {
            forEachTouchedCluster(state, lights[lightIndex])
            {
                lightIndexScratch[clusterCursors[it]++] = lightIndex
            }
        }

        uploadClusters(totalIndexCount)
        submitBuffers()
        enabled = totalIndexCount > 0
    }

    fun bind()
    {
        if (!initialized) return
        clusterBuffer.bindSubmittedRange()
        indexBuffer.bindSubmittedRange()
    }

    fun markSubmittedDataInUse()
    {
        if (!initialized || !submitted) return

        clusterBuffer.markSubmittedDataInUse()
        indexBuffer.markSubmittedDataInUse()
        submitted = false
    }

    fun destroy()
    {
        if (!initialized) return

        clusterBuffer.destroy()
        indexBuffer.destroy()
        initialized = false
        submitted = false
    }

    private fun clearAndSubmit()
    {
        gridWidth = 1
        gridHeight = 1
        gridDepth = DEFAULT_GRID_Z
        clusterCount = 1
        enabled = false

        clusterBuffer.fill(CLUSTER_INTS) { put(0, 0) }
        indexBuffer.fill(1) { put(0) }
        submitBuffers()
    }

    private inline fun forEachTouchedCluster(state: CameraRenderState, light: WorldRenderLight, action: (cluster: Int) -> Unit)
    {
        if (!state.frustum.intersectsSphere(light.position.x, light.position.y, light.position.z, light.radius))
            return

        tmpViewPos.set(light.position.x, light.position.y, light.position.z, 1f).mul(state.viewMatrix)
        val viewDepth = -tmpViewPos.z
        val radius = max(light.radius, 0.001f)
        val minDepth = max(nearPlane, viewDepth - radius)
        val maxDepth = min(farPlane, viewDepth + radius)
        if (maxDepth < minDepth)
            return

        if (!projectSphereBoundsToNdc(state, tmpViewPos.x, tmpViewPos.y, radius, minDepth, maxDepth))
            return

        val xMin = screenXToTile(state, xNdcProjectedMin).coerceIn(0, gridWidth - 1)
        val xMax = screenXToTile(state, xNdcProjectedMax).coerceIn(0, gridWidth - 1)
        val yMin = screenYToTile(state, yNdcProjectedMin).coerceIn(0, gridHeight - 1)
        val yMax = screenYToTile(state, yNdcProjectedMax).coerceIn(0, gridHeight - 1)
        val zMin = depthToSlice(minDepth).coerceIn(0, gridDepth - 1)
        val zMax = depthToSlice(maxDepth).coerceIn(0, gridDepth - 1)

        if (xMax < xMin || yMax < yMin || zMax < zMin)
            return

        for (z in zMin..zMax)
            for (y in yMin..yMax)
                for (x in xMin..xMax)
                    action((z * gridHeight + y) * gridWidth + x)
    }

    private fun screenXToTile(state: CameraRenderState, ndcX: Float): Int =
        floor(((ndcX * 0.5f + 0.5f) * state.screenWidth) / xTileSize).toInt()

    private fun screenYToTile(state: CameraRenderState, ndcY: Float): Int =
        floor(((ndcY * 0.5f + 0.5f) * state.screenHeight) / yTileSize).toInt()

    private fun depthToSlice(viewDepth: Float): Int =
        floor(ln(max(viewDepth, nearPlane) / nearPlane) * depthSliceScale).toInt()

    private fun projectSphereBoundsToNdc(
        state: CameraRenderState,
        xView: Float,
        yView: Float,
        radius: Float,
        minDepth: Float,
        maxDepth: Float
    ): Boolean {
        xNdcProjectedMin = Float.POSITIVE_INFINITY
        xNdcProjectedMax = Float.NEGATIVE_INFINITY
        yNdcProjectedMin = Float.POSITIVE_INFINITY
        yNdcProjectedMax = Float.NEGATIVE_INFINITY

        val xMin = xView - radius
        val xMax = xView + radius
        val yMin = yView - radius
        val yMax = yView + radius

        includeProjectedBoundsPoint(state, xMin, yMin, minDepth)
        includeProjectedBoundsPoint(state, xMin, yMax, minDepth)
        includeProjectedBoundsPoint(state, xMax, yMin, minDepth)
        includeProjectedBoundsPoint(state, xMax, yMax, minDepth)
        includeProjectedBoundsPoint(state, xMin, yMin, maxDepth)
        includeProjectedBoundsPoint(state, xMin, yMax, maxDepth)
        includeProjectedBoundsPoint(state, xMax, yMin, maxDepth)
        includeProjectedBoundsPoint(state, xMax, yMax, maxDepth)

        if (xNdcProjectedMin == Float.POSITIVE_INFINITY)
            return false

        val paddingX = CLUSTER_BOUNDS_PIXEL_PADDING * 2f / state.screenWidth
        val paddingY = CLUSTER_BOUNDS_PIXEL_PADDING * 2f / state.screenHeight
        xNdcProjectedMin -= paddingX
        xNdcProjectedMax += paddingX
        yNdcProjectedMin -= paddingY
        yNdcProjectedMax += paddingY
        return true
    }

    private fun includeProjectedBoundsPoint(state: CameraRenderState, xView: Float, yView: Float, viewDepth: Float)
    {
        tmpClipPos.set(xView, yView, -viewDepth, 1f).mul(state.projectionMatrix)
        if (abs(tmpClipPos.w) <= 0.000001f)
            return

        val ndcX = tmpClipPos.x / tmpClipPos.w
        val ndcY = tmpClipPos.y / tmpClipPos.w

        xNdcProjectedMin = min(xNdcProjectedMin, ndcX)
        xNdcProjectedMax = max(xNdcProjectedMax, ndcX)
        yNdcProjectedMin = min(yNdcProjectedMin, ndcY)
        yNdcProjectedMax = max(yNdcProjectedMax, ndcY)
    }

    private fun uploadClusters(totalIndexCount: Int)
    {
        clusterBuffer.fill(max(1, clusterCount) * CLUSTER_INTS)
        {
            for (cluster in 0 until clusterCount)
                put(clusterOffsets[cluster], clusterCounts[cluster])
        }

        indexBuffer.fill(max(1, totalIndexCount))
        {
            if (totalIndexCount > 0)
            {
                for (i in 0 until totalIndexCount) put(lightIndexScratch[i])
            }
            else put(0)
        }
    }

    private fun submitBuffers() = measure("submit clustered light buffers")
    {
        clusterBuffer.submit()
        indexBuffer.submit()
        submitted = true
    }

    companion object
    {
        const val CLUSTER_BUFFER_BINDING = 14
        const val CLUSTER_INDEX_BUFFER_BINDING = 15

        private const val BUFFER_SEGMENTS = 3
        private const val DEFAULT_TILE_SIZE = 64
        private const val DEFAULT_GRID_Z = 24
        private const val CLUSTER_BOUNDS_PIXEL_PADDING = 1f
        private const val CLUSTER_INTS = 2
    }
}