package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.StreamingFloatBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.world.views.WorldCameraRenderView
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Vector4f
import kotlin.math.*

class ClusteredLightGrid
{
    private lateinit var lightBuffer: StreamingFloatBufferObject
    private lateinit var shadowFaceBuffer: StreamingFloatBufferObject
    private lateinit var clusterBuffer: StreamingIntBufferObject
    private lateinit var indexBuffer: StreamingIntBufferObject

    private var clusterCounts = IntArray(0)
    private var clusterOffsets = IntArray(0)
    private var clusterCursors = IntArray(0)
    private var lightIndexScratch = IntArray(0)

    private val tmpViewPos = Vector4f()
    private val tmpClipPos = Vector4f()
    private var projectedMinNdcX = 0f
    private var projectedMaxNdcX = 0f
    private var projectedMinNdcY = 0f
    private var projectedMaxNdcY = 0f

    var xGrid           = 1;                 private set
    var yGrid           = 1;                 private set
    var zGrid           = DEFAULT_GRID_Z;    private set
    var zTileSize       = DEFAULT_TILE_SIZE; private set
    var yTileSize       = DEFAULT_TILE_SIZE; private set
    var nearPlane       = 0.05f;             private set
    var farPlane        = 1f;                private set
    var depthSliceScale = 1f;                private set
    var clusterCount    = 1;                 private set
    var lightCount      = 0;                 private set
    var shadowFaceCount = 0;                 private set
    var enabled         = false;             private set

    private var initialized = false

    fun init()
    {
        if (initialized)
            return

        lightBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(LOCAL_LIGHT_BUFFER_BINDING, LIGHT_FLOATS * 128, BUFFER_SEGMENTS)
        shadowFaceBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(LOCAL_SHADOW_FACE_BUFFER_BINDING, SHADOW_FACE_FLOATS * 64, BUFFER_SEGMENTS)
        clusterBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CLUSTER_BUFFER_BINDING, CLUSTER_INTS * 1024, BUFFER_SEGMENTS)
        indexBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CLUSTER_INDEX_BUFFER_BINDING, 4096, BUFFER_SEGMENTS)
        initialized = true
    }

    fun buildAndSubmit(view: WorldCameraRenderView, scene: WorldRenderScene, shadowAtlas: LocalShadowAtlas)
    {
        init()
        clearBuffers()

        val lights = scene.localLights
        
        xGrid = max(1, (view.screenWidth + zTileSize - 1) / zTileSize)
        yGrid = max(1, (view.screenHeight + yTileSize - 1) / yTileSize)
        zGrid = DEFAULT_GRID_Z
        clusterCount = xGrid * yGrid * zGrid
        lightCount = lights.size
        shadowFaceCount = shadowAtlas.getFaceCount()
        nearPlane = max(0.01f, view.nearPlane)
        farPlane = max(nearPlane + 0.01f, view.farPlane)
        depthSliceScale = zGrid.toFloat() / ln(farPlane / nearPlane)

        if (clusterCounts.size < clusterCount)
        {
            clusterCounts  = IntArray(clusterCount)
            clusterOffsets = IntArray(clusterCount)
            clusterCursors = IntArray(clusterCount)
        }
        clusterCounts.fill(0, 0, clusterCount)

        for (lightIndex in 0 until lights.size)
            forEachTouchedCluster(view, lights[lightIndex]) { clusterCounts[it]++ }

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
            forEachTouchedCluster(view, lights[lightIndex])
            {
                lightIndexScratch[clusterCursors[it]++] = lightIndex
            }
        }

        uploadLights(scene)
        uploadShadowFaces(shadowAtlas)
        uploadClusters(totalIndexCount)
        submitBuffers()

        enabled = lights.size > 0 && totalIndexCount > 0
    }

    fun clearAndSubmit()
    {
        init()
        clearBuffers()

        xGrid = 1
        yGrid = 1
        zGrid = DEFAULT_GRID_Z
        clusterCount = 1
        lightCount = 0
        shadowFaceCount = 0
        enabled = false

        lightBuffer.fill(LIGHT_FLOATS) { repeat(LIGHT_FLOATS) { put(0f) } }
        shadowFaceBuffer.fill(SHADOW_FACE_FLOATS) { repeat(SHADOW_FACE_FLOATS) { put(0f) } }
        clusterBuffer.fill(CLUSTER_INTS) { put(0, 0) }
        indexBuffer.fill(1) { put(0) }

        submitBuffers()
    }

    fun bind(program: ShaderProgram)
    {
        if (!initialized)
            return

        lightBuffer.bindSubmittedRange()
        shadowFaceBuffer.bindSubmittedRange()
        clusterBuffer.bindSubmittedRange()
        indexBuffer.bindSubmittedRange()

        program.setUniform("uClusteredLightingEnabled", enabled)
        program.setUniform("uClusterGridSize", xGrid, yGrid, zGrid)
        program.setUniform("uClusterTileSize", zTileSize.toFloat(), yTileSize.toFloat())
        program.setUniform("uClusterNearPlane", nearPlane)
        program.setUniform("uClusterDepthSliceScale", depthSliceScale)
        program.setUniform("uClusterCount", clusterCount)
        program.setUniform("uClusterLightCount", lightCount)
        program.setUniform("uLocalShadowFaceCount", shadowFaceCount)
    }

    fun destroy()
    {
        if (!initialized)
            return

        lightBuffer.destroy()
        shadowFaceBuffer.destroy()
        clusterBuffer.destroy()
        indexBuffer.destroy()
        initialized = false
    }

    fun markSubmittedDataInUse()
    {
        if (!initialized)
            return

        lightBuffer.markSubmittedDataInUse()
        shadowFaceBuffer.markSubmittedDataInUse()
        clusterBuffer.markSubmittedDataInUse()
        indexBuffer.markSubmittedDataInUse()
    }

    private inline fun forEachTouchedCluster(view: WorldCameraRenderView, light: WorldRenderLight, action: (cluster: Int) -> Unit)
    {
        if (!view.frustum.intersectsSphere(light.position.x, light.position.y, light.position.z, light.radius))
            return

        tmpViewPos.set(light.position.x, light.position.y, light.position.z, 1f).mul(view.viewMatrix)
        val viewDepth = -tmpViewPos.z
        val radius = max(light.radius, 0.001f)
        val minDepth = max(nearPlane, viewDepth - radius)
        val maxDepth = min(farPlane, viewDepth + radius)
        if (maxDepth < minDepth)
            return

        if (!projectSphereBoundsToNdc(view, tmpViewPos.x, tmpViewPos.y, radius, minDepth, maxDepth))
            return

        val xMin = screenXToTile(view, projectedMinNdcX).coerceIn(0, xGrid - 1)
        val xMax = screenXToTile(view, projectedMaxNdcX).coerceIn(0, xGrid - 1)
        val yMin = screenYToTile(view, projectedMinNdcY).coerceIn(0, yGrid - 1)
        val yMax = screenYToTile(view, projectedMaxNdcY).coerceIn(0, yGrid - 1)
        val zMin = depthToSlice(minDepth).coerceIn(0, zGrid - 1)
        val zMax = depthToSlice(maxDepth).coerceIn(0, zGrid - 1)

        if (xMax < xMin || yMax < yMin || zMax < zMin)
            return

        for (z in zMin..zMax)
            for (y in yMin..yMax)
                for (x in xMin..xMax)
                    action((z * yGrid + y) * xGrid + x)
    }

    private fun screenXToTile(view: WorldCameraRenderView, ndcX: Float): Int =
        floor(((ndcX * 0.5f + 0.5f) * view.screenWidth) / zTileSize).toInt()

    private fun screenYToTile(view: WorldCameraRenderView, ndcY: Float): Int =
        floor(((ndcY * 0.5f + 0.5f) * view.screenHeight) / yTileSize).toInt()

    private fun depthToSlice(viewDepth: Float): Int =
        floor(ln(max(viewDepth, nearPlane) / nearPlane) * depthSliceScale).toInt()
    
    private fun projectSphereBoundsToNdc(view: WorldCameraRenderView, xView: Float, yView: Float, radius: Float, minDepth: Float, maxDepth: Float): Boolean 
    {
        projectedMinNdcX = Float.POSITIVE_INFINITY
        projectedMaxNdcX = Float.NEGATIVE_INFINITY
        projectedMinNdcY = Float.POSITIVE_INFINITY
        projectedMaxNdcY = Float.NEGATIVE_INFINITY

        val xMin = xView - radius
        val xMax = xView + radius
        val yMin = yView - radius
        val yMax = yView + radius

        includeProjectedBoundsPoint(view, xMin, yMin, minDepth)
        includeProjectedBoundsPoint(view, xMin, yMax, minDepth)
        includeProjectedBoundsPoint(view, xMax, yMin, minDepth)
        includeProjectedBoundsPoint(view, xMax, yMax, minDepth)
        includeProjectedBoundsPoint(view, xMin, yMin, maxDepth)
        includeProjectedBoundsPoint(view, xMin, yMax, maxDepth)
        includeProjectedBoundsPoint(view, xMax, yMin, maxDepth)
        includeProjectedBoundsPoint(view, xMax, yMax, maxDepth)

        if (projectedMinNdcX == Float.POSITIVE_INFINITY)
            return false

        val paddingX = CLUSTER_BOUNDS_PIXEL_PADDING * 2f / view.screenWidth
        val paddingY = CLUSTER_BOUNDS_PIXEL_PADDING * 2f / view.screenHeight
        projectedMinNdcX -= paddingX
        projectedMaxNdcX += paddingX
        projectedMinNdcY -= paddingY
        projectedMaxNdcY += paddingY
        return true
    }

    private fun includeProjectedBoundsPoint(view: WorldCameraRenderView, xView: Float, yView: Float, viewDepth: Float)
    {
        tmpClipPos.set(xView, yView, -viewDepth, 1f).mul(view.projectionMatrix)
        if (abs(tmpClipPos.w) <= 0.000001f)
            return

        val ndcX = tmpClipPos.x / tmpClipPos.w
        val ndcY = tmpClipPos.y / tmpClipPos.w

        projectedMinNdcX = min(projectedMinNdcX, ndcX)
        projectedMaxNdcX = max(projectedMaxNdcX, ndcX)
        projectedMinNdcY = min(projectedMinNdcY, ndcY)
        projectedMaxNdcY = max(projectedMaxNdcY, ndcY)
    }

    private fun uploadLights(scene: WorldRenderScene)
    {
        val lights = scene.localLights
        val count = max(1, lights.size)
        lightBuffer.fill(count * LIGHT_FLOATS)
        {
            if (lights.size == 0)
            {
                repeat(LIGHT_FLOATS) { put(0f) }
                return@fill
            }

            for (i in 0 until lights.size)
            {
                val light = lights[i]
                val color = light.color.asLinear()
                val isSpotLight = if (light.isSpotLight) 1f else 0f
                val castsShadow = if (light.shadowEnabled && light.shadowFaceOffset >= 0 && light.shadowFaceCount > 0) 1f else 0f
                val shadowBias = if (castsShadow > 0f) light.shadowBias else -1f

                put(light.position.x, light.position.y, light.position.z, light.radius)
                put(color.red, color.green, color.blue, light.direction.x)
                put(light.direction.y, light.direction.z, cos(light.outerConeAngle.toRadians()), cos(light.innerConeAngle.toRadians()))
                put(isSpotLight, shadowBias, light.shadowFaceOffset.toFloat(), light.shadowFaceCount.toFloat())
            }
        }
    }

    private fun uploadShadowFaces(shadowAtlas: LocalShadowAtlas)
    {
        val faceCount = shadowAtlas.getFaceCount()
        shadowFaceBuffer.fill(max(1, faceCount) * SHADOW_FACE_FLOATS)
        {
            if (faceCount == 0)
            {
                repeat(SHADOW_FACE_FLOATS) { put(0f) }
                return@fill
            }

            for (i in 0 until faceCount)
            {
                val face = shadowAtlas.getFace(i)
                val atlas = face.atlasScaleBias
                put(atlas.x, atlas.y, atlas.z, atlas.w)
                put(face.viewProjection)
            }
        }
    }

    private fun uploadClusters(totalIndexCount: Int)
    {
        clusterBuffer.fill(max(1, clusterCount) * CLUSTER_INTS)
        {
            if (clusterCount > 0)
            {
                for (cluster in 0 until clusterCount) 
                    put(clusterOffsets[cluster], clusterCounts[cluster])
            } 
            else put(0, 0)
        }

        indexBuffer.fill(max(1, totalIndexCount))
        {
            if (totalIndexCount > 0)
            {
                for (i in 0 until totalIndexCount) 
                    put(lightIndexScratch[i])
            }
            else put(0)
        }
    }

    private fun clearBuffers()
    {
        lightBuffer.clear()
        shadowFaceBuffer.clear()
        clusterBuffer.clear()
        indexBuffer.clear()
    }

    private fun submitBuffers() = measure("submit light buffers")
    {
        lightBuffer.submit()
        shadowFaceBuffer.submit()
        clusterBuffer.submit()
        indexBuffer.submit()
    }

    companion object
    {
        const val LOCAL_LIGHT_BUFFER_BINDING = 13
        const val CLUSTER_BUFFER_BINDING = 14
        const val CLUSTER_INDEX_BUFFER_BINDING = 15
        const val LOCAL_SHADOW_FACE_BUFFER_BINDING = 16

        private const val BUFFER_SEGMENTS = 3
        private const val DEFAULT_TILE_SIZE = 64
        private const val DEFAULT_GRID_Z = 24
        private const val CLUSTER_BOUNDS_PIXEL_PADDING = 1f
        private const val CLUSTER_INTS = 2
        private const val LIGHT_VEC4S = 4
        private const val LIGHT_FLOATS = LIGHT_VEC4S * 4
        private const val SHADOW_FACE_VEC4S = 5
        private const val SHADOW_FACE_FLOATS = SHADOW_FACE_VEC4S * 4
    }
}