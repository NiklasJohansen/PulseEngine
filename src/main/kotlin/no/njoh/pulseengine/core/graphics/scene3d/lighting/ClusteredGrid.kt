package no.njoh.pulseengine.core.graphics.scene3d.lighting

import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderState
import no.njoh.pulseengine.core.graphics.gpu.buffer.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

open class ClusteredGrid
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

    var lastFrameRequested = -1
    var buildTimeNs = 0L
    
    // Buffer stores (absolute offset, count) pairs for all clusters, followed by item indices
    lateinit var clusterBuffer: StreamingIntBufferObject  private set

    private var clusterCounts    = IntArray(0)
    private var clusterOffsets   = IntArray(0)
    private var indices          = IntArray(0)
    private var clusterCursors   = IntArray(0)
    private var totalIndexCount  = 0
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

        clusterBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CLUSTER_INTS * 1024, BUFFER_SEGMENTS)
        initialized = true
    }
    
    fun build(state: CameraRenderState, bounds: FloatArray, itemCount: Int)
    {
        val startTime = System.nanoTime()

        totalIndexCount = 0
        require(itemCount >= 0 && itemCount <= bounds.size / 4)

        nearPlane       = max(0.01f, state.nearPlane)
        farPlane        = max(nearPlane + 0.01f, state.farPlane)
        depthSliceScale = gridDepth.toFloat() / ln(farPlane / nearPlane)

        if (itemCount == 0)
        {
            disable()
            return
        }

        gridWidth    = max(1, (state.screenWidth + xTileSize - 1) / xTileSize)
        gridHeight   = max(1, (state.screenHeight + yTileSize - 1) / yTileSize)
        gridDepth    = DEFAULT_GRID_Z
        clusterCount = gridWidth * gridHeight * gridDepth

        if (clusterCounts.size < clusterCount)
        {
            clusterCounts  = IntArray(clusterCount)
            clusterOffsets = IntArray(clusterCount)
            clusterCursors = IntArray(clusterCount)
        }
        clusterCounts.fill(0, 0, clusterCount)

        for (itemIndex in 0 until itemCount)
        {
            forEachTouchedCluster(state, bounds, itemIndex * 4) { clusterCounts[it]++ }
        }

        totalIndexCount = 0
        for (cluster in 0 until clusterCount)
        {
            clusterOffsets[cluster] = totalIndexCount
            clusterCursors[cluster] = totalIndexCount
            totalIndexCount += clusterCounts[cluster]
        }

        if (totalIndexCount == 0)
        {
            disable()
            return
        }

        if (indices.size < totalIndexCount)
            indices = IntArray(totalIndexCount)

        for (itemIndex in 0 until itemCount)
        {
            forEachTouchedCluster(state, bounds, offset = itemIndex * 4)
            {
                indices[clusterCursors[it]++] = itemIndex
            }
        }

        enabled = totalIndexCount > 0
        buildTimeNs = System.nanoTime() - startTime
    }

    fun submit() = measure("Cluster buffers")
    {
        if (!enabled) return@measure

        init()

        clusterBuffer.clear()
        
        val indexOffset = clusterCount * CLUSTER_INTS
        clusterBuffer.fill(indexOffset + totalIndexCount)
        {
            for (cluster in 0 until clusterCount)
                put(clusterOffsets[cluster] + indexOffset, clusterCounts[cluster])

            for (i in 0 until totalIndexCount) put(indices[i])
        }

        clusterBuffer.submit()
        submitted = true
    }
    
    fun markSubmittedDataInUse()
    {
        if (!initialized || !submitted) return

        clusterBuffer.markSubmittedDataInUse()
        submitted = false
    }

    fun destroy()
    {
        if (!initialized) return

        clusterBuffer.destroy()
        initialized = false
        submitted = false
    }

    private fun disable()
    {
        enabled      = false
        clusterCount = 1
        gridWidth    = 1
        gridHeight   = 1
        gridDepth    = DEFAULT_GRID_Z
    }

    private inline fun forEachTouchedCluster(state: CameraRenderState, bounds: FloatArray, offset: Int, action: (cluster: Int) -> Unit)
    {
        val radius = bounds[offset + 3]
        if (radius < 0f) 
            return

        val x = bounds[offset]
        val y = bounds[offset + 1]
        val z = bounds[offset + 2]

        if (!state.frustum.intersectsSphere(x, y, z, radius))
            return

        tmpViewPos.set(x, y, z, 1f).mul(state.viewMatrix)
        val viewDepth = -tmpViewPos.z
        val range = max(radius, 0.001f)
        val minDepth = max(nearPlane, viewDepth - range)
        val maxDepth = min(farPlane,  viewDepth + range)
        if (maxDepth < minDepth)
            return

        if (!projectSphereBoundsToNdc(state, tmpViewPos.x, tmpViewPos.y, range, minDepth, maxDepth))
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

    companion object
    {
        private const val BUFFER_SEGMENTS              = 3
        private const val DEFAULT_TILE_SIZE            = 64
        private const val DEFAULT_GRID_Z               = 24
        private const val CLUSTER_BOUNDS_PIXEL_PADDING = 1f
        private const val CLUSTER_INTS                 = 2
    }
}