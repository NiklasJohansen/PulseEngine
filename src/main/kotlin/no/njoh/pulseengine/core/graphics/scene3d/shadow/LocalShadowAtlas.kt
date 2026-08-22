package no.njoh.pulseengine.core.graphics.scene3d.shadow

import gnu.trove.list.array.TIntArrayList
import gnu.trove.map.hash.THashMap
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderLight
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.primitives.StaticList
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.text.compareTo

class LocalShadowAtlas
{
    var enabled = true
    var resolution = 4096
    var shadowFaceResolution = 512
    var maxShadowUpdatesPerFrame = 3

    private val shadowFaces = DynamicList<ShadowFace>(64)
    private val activeShadowFaces = DynamicList<ShadowFace>(64)
    private val blocks = THashMap<Long, ShadowBlock>()
    private val activeBlocks = DynamicList<ShadowBlock>(64)
    private val updateCandidates = DynamicList<ShadowBlock>(64)
    private val activeRequests = DynamicList<ShadowRequest>(64)
    private val freeRequests = DynamicList<ShadowRequest>(64)
    private val shadowFaceIndicesToRender = TIntArrayList(64)

    private var usedFaceSlots = BooleanArray(0)
    private var frameIndex = 0
    private var lastResolution = 0
    private var lastCellSize = 0

    private val tmpView = Matrix4f()
    private val tmpProjection = Matrix4f()
    private val tmpCenter = Vector3f()

    private val blockUpdateComparator = Comparator<ShadowBlock> { a, b ->
        val score = b.updateScore().compareTo(a.updateScore())
        if (score != 0) score else a.key.compareTo(b.key)
    }

    fun update(scene: RenderScene, cameraPosition: Vector3f? = null)
    {
        activeRequests.forEach { freeRequests += it }
        activeRequests.clear()
        activeBlocks.clear()
        updateCandidates.clear()
        activeShadowFaces.clear()
        shadowFaceIndicesToRender.resetQuick()

        scene.localLights.forEach()
        {
            it.shadowFaceOffset = -1
            it.shadowFaceCount = 0
        }

        if (!enabled || scene.localLights.isEmpty())
        {
            frameIndex++
            return
        }

        val cellSize = shadowFaceResolution.coerceIn(MIN_FACE_SIZE, max(MIN_FACE_SIZE, resolution))
        val cellsPerSide = max(1, resolution / cellSize)
        val maxFaceCount = cellsPerSide * cellsPerSide
        updateLayout(cellSize, maxFaceCount)

        collectRequests(scene.localLights, cameraPosition, cellSize)
        allocateRequestedBlocks(maxFaceCount, cellSize, cellsPerSide)
        scheduleBlockUpdates()
        publishValidBlocks()
        pruneStaleBlocks()
        frameIndex++
    }

    private fun updateLayout(cellSize: Int, maxFaceCount: Int)
    {
        if (lastResolution != resolution || lastCellSize != cellSize)
        {
            blocks.forEach { it.value.invalidateOwnedFaces() }
            blocks.clear()
            shadowFaces.forEach { it.invalidate() }
            lastResolution = resolution
            lastCellSize = cellSize
        }

        if (usedFaceSlots.size != maxFaceCount)
            usedFaceSlots = BooleanArray(maxFaceCount)
        else
            usedFaceSlots.fill(false)
    }

    private fun collectRequests(lights: DynamicList<RenderLight>, cameraPosition: Vector3fc?, cellSize: Int)
    {
        for (submissionIndex in 0 until lights.size)
        {
            val light = lights[submissionIndex]
            if (!light.shadowEnabled || light.shadowResolution <= 0)
                continue

            val request = freeRequests.removeLastOrNull() ?: ShadowRequest()
            request.light = light
            request.key = light.shadowBlockKey(submissionIndex)
            request.faceCount = if (light.isSpotLight) 1 else POINT_LIGHT_SHADOW_FACE_COUNT
            request.faceSize = light.shadowResolution.coerceIn(MIN_FACE_SIZE, cellSize)
            request.importance = lightShadowUpdateImportance(light, cameraPosition)
            activeRequests += request
        }

        activeRequests.sortWith(requestPriorityComparator)
    }

    private fun allocateRequestedBlocks(maxFaceCount: Int, cellSize: Int, cellsPerSide: Int)
    {
        activeRequests.forEach { request ->

            var block = blocks[request.key]
            val reusable = block != null &&
                block.faceCount == request.faceCount &&
                block.faceOffset + request.faceCount <= maxFaceCount &&
                isFaceRangeFree(block.faceOffset, block.faceCount)

            if (!reusable)
            {
                val newOffset = findFreeFaceRange(request.faceCount, maxFaceCount)
                if (newOffset < 0)
                {
                    block?.invalidateOwnedFaces()
                    blocks.remove(request.key)
                    return@forEach
                }

                // A higher-priority request may claim cells held by an inactive cached block
                blocks.retainEntries { key, cached ->
                    val keep = key == request.key || !cached.overlaps(newOffset, request.faceCount)
                    if (!keep) cached.invalidateOwnedFaces()
                    keep
                }

                block = block ?: ShadowBlock(request.key)
                if (block.faceOffset != newOffset || block.faceCount != request.faceCount)
                {
                    block.invalidateOwnedFaces()
                    block.faceOffset = newOffset
                    block.faceCount = request.faceCount
                    block.contentValid = false
                    block.dirty = true
                }
                blocks[request.key] = block
            }

            markFaceSlotsUsed(block.faceOffset, block.faceCount)
            configureBlock(block, request, cellSize, cellsPerSide)
            activeBlocks += block
        }
    }

    private fun configureBlock(block: ShadowBlock, request: ShadowRequest, cellSize: Int, cellsPerSide: Int)
    {
        block.light = request.light
        block.importance = request.importance
        block.desiredUpdateInterval = shadowUpdateIntervalFor(request.importance)
        block.lastRequestedFrame = frameIndex

        var projectionChanged = false
        for (faceInLight in 0 until block.faceCount)
        {
            val slot = block.faceOffset + faceInLight
            val face = getOrCreateFace(slot)
            val ownerChanged = face.blockKey != block.key || face.faceInLight != faceInLight || face.faceCountInLight != block.faceCount
            val sizeChanged = face.size != 0 && face.size != request.faceSize

            if (ownerChanged || sizeChanged)
            {
                block.contentValid = false
                face.invalidate()
            }

            configureFaceLayout(face, slot, faceInLight, block, request.faceSize, cellSize, cellsPerSide)
            
            val pendingProjection = if (request.light.isSpotLight)
                request.light.getSpotShadowViewProjection()
            else
                request.light.getPointShadowViewProjection(faceInLight)

            if (block.contentValid && !face.viewProjection.equals(pendingProjection, SHADOW_MATRIX_EPSILON))
                projectionChanged = true

            face.pendingViewProjection.set(pendingProjection)
        }

        if (projectionChanged)
        {
            block.contentValid = false
            block.invalidateOwnedFaceValidity()
        }

        val framesSinceRender = frameIndex - block.lastRenderedFrame
        block.dirty = !block.contentValid || framesSinceRender >= block.desiredUpdateInterval
    }

    private fun configureFaceLayout(
        face: ShadowFace,
        slot: Int,
        faceInLight: Int,
        block: ShadowBlock,
        faceSize: Int,
        cellSize: Int,
        cellsPerSide: Int
    ) {
        val xCell = slot % cellsPerSide
        val yCell = slot / cellsPerSide
        face.index = slot
        face.x = xCell * cellSize
        face.y = yCell * cellSize
        face.size = faceSize
        face.blockKey = block.key
        face.faceInLight = faceInLight
        face.faceCountInLight = block.faceCount
        face.atlasScaleBias.set(
            faceSize.toFloat() / resolution.toFloat(),
            faceSize.toFloat() / resolution.toFloat(),
            face.x.toFloat() / resolution.toFloat(),
            face.y.toFloat() / resolution.toFloat()
        )
    }

    private fun scheduleBlockUpdates()
    {
        val budget = max(0, maxShadowUpdatesPerFrame)
        if (budget == 0) return

        activeBlocks.forEach { if (it.dirty) updateCandidates += it }
        updateCandidates.sortWith(blockUpdateComparator)

        var scheduledFaceCount = 0
        updateCandidates.forEach { block ->
            val oversized = block.faceCount > budget
            val fits = scheduledFaceCount + block.faceCount <= budget
            // An indivisible point block may exceed the total soft budget, but it must be the
            // only selected block. This caps the overrun at pointFaceCount - 1 faces.
            if ((oversized && scheduledFaceCount != 0) || (!oversized && !fits))
                return@forEach

            stageBlockRender(block)
            scheduledFaceCount += block.faceCount
        }
    }

    private fun stageBlockRender(block: ShadowBlock)
    {
        for (faceInLight in 0 until block.faceCount)
        {
            val face = shadowFaces[block.faceOffset + faceInLight]
            face.viewProjection.set(face.pendingViewProjection)
            face.valid = true
            shadowFaceIndicesToRender.add(face.index)
        }
        block.contentValid = true
        block.dirty = false
        block.lastRenderedFrame = frameIndex
    }

    private fun publishValidBlocks()
    {
        activeBlocks.forEach { block ->

            val light = block.light ?: return@forEach
            if (!block.contentValid)
                return@forEach

            val bufferOffset = activeShadowFaces.size
            var allFacesValid = true
            for (faceInLight in 0 until block.faceCount)
            {
                val face = shadowFaces[block.faceOffset + faceInLight]
                if (!face.valid)
                {
                    allFacesValid = false
                    break
                }
                face.bufferIndex = activeShadowFaces.size
                activeShadowFaces += face
            }

            if (allFacesValid)
            {
                light.shadowFaceOffset = bufferOffset
                light.shadowFaceCount = block.faceCount
            }
            else
            {
                while (activeShadowFaces.size > bufferOffset)
                    activeShadowFaces.removeLastOrNull()
                block.contentValid = false
            }
        }
    }

    private fun pruneStaleBlocks()
    {
        blocks.retainEntries { _, block ->
            val retain = frameIndex - block.lastRequestedFrame <= BLOCK_RETENTION_FRAMES
            if (!retain) block.invalidateOwnedFaces()
            retain
        }
    }

    private fun findFreeFaceRange(faceCount: Int, maxFaceCount: Int): Int
    {
        var offset = 0
        while (offset + faceCount <= maxFaceCount)
        {
            if (isFaceRangeFree(offset, faceCount)) return offset
            offset++
        }
        return -1
    }

    private fun isFaceRangeFree(offset: Int, faceCount: Int): Boolean
    {
        for (i in offset until offset + faceCount)
            if (usedFaceSlots[i]) return false
        return true
    }

    private fun markFaceSlotsUsed(offset: Int, faceCount: Int)
    {
        for (i in offset until offset + faceCount)
            usedFaceSlots[i] = true
    }

    private fun getOrCreateFace(index: Int): ShadowFace
    {
        while (shadowFaces.size <= index)
            shadowFaces += ShadowFace()
        return shadowFaces[index]
    }

    private fun ShadowBlock.invalidateOwnedFaces()
    {
        invalidateOwnedFaceValidity()
        contentValid = false
        dirty = true
    }

    private fun ShadowBlock.invalidateOwnedFaceValidity()
    {
        if (faceOffset < 0) return
        for (i in 0 until faceCount)
        {
            val slot = faceOffset + i
            if (slot < shadowFaces.size && shadowFaces[slot].blockKey == key)
                shadowFaces[slot].invalidate()
        }
    }

    private fun ShadowBlock.updateScore(): Float
    {
        val framesSinceRender = max(0, frameIndex - lastRenderedFrame)
        val age = min(framesSinceRender, MAX_SCORE_AGE).toFloat()
        if (!contentValid)
            return INVALID_BLOCK_UPDATE_SCORE + age + importance

        val overdue = framesSinceRender.toFloat() / desiredUpdateInterval.toFloat()
        return when
        {
            framesSinceRender >= MAX_SHADOW_UPDATE_INTERVAL -> STARVED_BLOCK_UPDATE_SCORE + importance + overdue
            overdue >= 1f -> OVERDUE_BLOCK_UPDATE_SCORE + importance + overdue
            else -> BACKGROUND_BLOCK_UPDATE_SCORE + importance * BACKGROUND_IMPORTANCE_WEIGHT + overdue
        }
    }

    private fun RenderLight.shadowBlockKey(submissionIndex: Int): Long =
        if (shadowId > 0L) shadowId else -(submissionIndex + 1L)

    private fun lightShadowUpdateImportance(light: RenderLight, cameraPosition: Vector3fc?): Float
    {
        val manualImportance = max(0f, light.shadowImportance)
        val brightness = max(light.color.red, max(light.color.green, light.color.blue))
        val brightnessFactor = min(max(0.1f, brightness), 8f)
        val range = max(1f, light.range)
        val rangeFactor = min(max(0.5f, sqrt(range) * 0.25f), 4f)
        var importance = manualImportance * brightnessFactor * rangeFactor

        if (cameraPosition == null)
            return max(MIN_SHADOW_UPDATE_IMPORTANCE, importance)

        val dx = light.position.x - cameraPosition.x()
        val dy = light.position.y - cameraPosition.y()
        val dz = light.position.z - cameraPosition.z()
        val distanceToLight = sqrt(dx * dx + dy * dy + dz * dz)
        val distanceToInfluence = max(1f, distanceToLight - range)
        val distanceFactor = min(max(range / (range + distanceToInfluence), 0.05f), 1f)
        importance *= distanceFactor

        return max(MIN_SHADOW_UPDATE_IMPORTANCE, importance)
    }

    private fun shadowUpdateIntervalFor(importance: Float): Int = when
    {
        importance >= 12f   -> 1
        importance >= 6f    -> 2
        importance >= 3f    -> 4
        importance >= 1.5f  -> 8
        importance >= 0.75f -> 16
        importance >= 0.35f -> 32
        importance >= USEFUL_SHADOW_IMPORTANCE -> 60
        else -> 120
    }

    private fun RenderLight.getSpotShadowViewProjection(): Matrix4f
    {
        val up = if (abs(direction.dot(WORLD_UP)) > 0.99f) WORLD_FORWARD else WORLD_UP
        tmpCenter.set(position).add(direction)
        val fov = min(max(outerConeAngle * 2f, 1f), 178f).toRadians()
        val far = getShadowFarPlane()
        val near = getShadowNearPlane(far)
        tmpView.identity().lookAt(position, tmpCenter, up)
        return tmpProjection.identity().perspective(fov, 1f, near, far).mul(tmpView)
    }

    private fun RenderLight.getPointShadowViewProjection(faceIndex: Int): Matrix4f
    {
        val direction = POINT_DIRECTIONS[faceIndex]
        val far = getShadowFarPlane()
        val near = getShadowNearPlane(far)
        tmpCenter.set(position).add(direction)
        tmpView.identity().lookAt(position, tmpCenter, POINT_UPS[faceIndex])
        return tmpProjection.identity().perspective(90f.toRadians(), 1f, near, far).mul(tmpView)
    }

    private fun RenderLight.getShadowFarPlane() = max(range, MIN_SHADOW_NEAR_PLANE + MIN_SHADOW_DEPTH_RANGE)

    private fun RenderLight.getShadowNearPlane(farPlane: Float) =
        shadowNearPlane.coerceIn(MIN_SHADOW_NEAR_PLANE, farPlane - MIN_SHADOW_DEPTH_RANGE)

    fun getShadowFace(index: Int) = shadowFaces[index]

    fun getActiveShadowFaces(): StaticList<ShadowFace> = activeShadowFaces

    fun getNumberOfShadowFacesToRender() = shadowFaceIndicesToRender.size()

    fun getShadowFaceIndexToRender(index: Int) = shadowFaceIndicesToRender[index]

    class ShadowFace
    {
        val viewProjection = Matrix4f()
        val pendingViewProjection = Matrix4f()
        val atlasScaleBias = Vector4f()
        var index = -1
        var x = 0
        var y = 0
        var size = 0
        var bufferIndex = -1
        var valid = false
        var blockKey = 0L
        var faceInLight = 0
        var faceCountInLight = 0

        fun invalidate()
        {
            valid = false
            bufferIndex = -1
            viewProjection.identity()
        }
    }

    private class ShadowRequest
    {
        lateinit var light: RenderLight
        var key = 0L
        var faceCount = 0
        var faceSize = 0
        var importance = 0f
    }

    private inner class ShadowBlock(val key: Long)
    {
        var faceOffset = -1
        var faceCount = 0
        var importance = 0f
        var desiredUpdateInterval = 120
        var lastRenderedFrame = INITIAL_LAST_RENDERED_FRAME
        var lastRequestedFrame = frameIndex
        var contentValid = false
        var dirty = true
        var light: RenderLight? = null

        fun overlaps(offset: Int, count: Int) = faceOffset < offset + count && offset < faceOffset + faceCount
    }

    companion object
    {
        const val POINT_LIGHT_SHADOW_FACE_COUNT = 6

        private const val MIN_FACE_SIZE = 64
        private const val BLOCK_RETENTION_FRAMES = 120
        private const val USEFUL_SHADOW_IMPORTANCE = 0.1f
        private const val MIN_SHADOW_UPDATE_IMPORTANCE = 0.01f
        private const val BACKGROUND_IMPORTANCE_WEIGHT = 0.1f
        private const val MAX_SHADOW_UPDATE_INTERVAL = 240
        private const val MAX_SCORE_AGE = 1000
        private const val INVALID_BLOCK_UPDATE_SCORE = 30_000f
        private const val STARVED_BLOCK_UPDATE_SCORE = 20_000f
        private const val OVERDUE_BLOCK_UPDATE_SCORE = 10_000f
        private const val BACKGROUND_BLOCK_UPDATE_SCORE = 0f
        private const val INITIAL_LAST_RENDERED_FRAME = -1_000_000
        private const val SHADOW_MATRIX_EPSILON = 0.00001f
        private const val MIN_SHADOW_NEAR_PLANE = 0.001f
        private const val MIN_SHADOW_DEPTH_RANGE = 0.01f

        private val WORLD_UP = Vector3f(0f, 1f, 0f)
        private val WORLD_FORWARD = Vector3f(0f, 0f, 1f)
        private val WORLD_BACKWARD = Vector3f(0f, 0f, -1f)
        private val WORLD_DOWN = Vector3f(0f, -1f, 0f)

        private val POINT_DIRECTIONS = arrayOf(
            Vector3f(1f, 0f, 0f), Vector3f(-1f, 0f, 0f),
            Vector3f(0f, 1f, 0f), Vector3f(0f, -1f, 0f),
            Vector3f(0f, 0f, 1f), Vector3f(0f, 0f, -1f)
        )

        private val POINT_UPS = arrayOf(
            WORLD_DOWN, WORLD_DOWN, WORLD_FORWARD, WORLD_BACKWARD, WORLD_DOWN, WORLD_DOWN
        )

        private val requestPriorityComparator = Comparator<ShadowRequest> { a, b ->
            val importance = b.importance.compareTo(a.importance)
            if (importance != 0) importance else a.key.compareTo(b.key)
        }
    }
}