package no.njoh.pulseengine.core.graphics.scene3d.shadow

import gnu.trove.list.array.TIntArrayList
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

class LocalShadowAtlas
{
    var enabled = true
    var resolution = 4096
    var shadowFaceResolution = 512
    var maxShadowFacesPerFrame = 3

    private val shadowFaces = DynamicList<ShadowFace>(64)
    private val activeShadowFaces = DynamicList<ShadowFace>(64)
    private val shadowBlocks = DynamicList<ShadowBlock>(64)
    private val reservedBlocks = DynamicList<ShadowBlock>(64)
    private val activeLights = DynamicList<RenderLight>(64)
    private var usedFaceSlots = BooleanArray(0)
    private val activeLightsFaceOffsets = TIntArrayList(64)
    private val activeLightsFaceCounts = TIntArrayList(64)
    private val shadowFaceIndicesToRender = TIntArrayList(64)
    private var shadowFaceLastUpdatedFrameIndex = IntArray(0)

    private var frameIndex = 0
    private var lastResolution = 0
    private var lastCellSize = 0

    private val tmpView = Matrix4f()
    private val tmpProjection = Matrix4f()
    private val tmpCenter = Vector3f()
    private val tmpCandidatesToRender = TIntArrayList(64)

    fun update(scene: RenderScene, cameraPosition: Vector3f? = null)
    {
        activeLights.clear()
        reservedBlocks.clear()
        activeShadowFaces.clear()
        activeLightsFaceOffsets.resetQuick()
        activeLightsFaceCounts.resetQuick()
        shadowFaceIndicesToRender.resetQuick()

        val lights = scene.localLights
        for (i in 0 until lights.size)
        {
            lights[i].shadowFaceOffset = -1
            lights[i].shadowFaceCount = 0
        }

        if (!enabled || lights.isEmpty()) 
            return

        val tileSize = shadowFaceResolution.coerceIn(64, max(64, resolution))
        val facesPerSide = max(1, resolution / tileSize)
        val maxFaceCount = facesPerSide * facesPerSide

        updateCollectionsIfLayoutChanged(tileSize, maxFaceCount)

        reserveCurrentLightBlocks(lights, maxFaceCount)

        configureShadowFaces(lights, maxFaceCount, tileSize, cameraPosition, facesPerSide)

        updateShadowFaceBufferIndices()

        selectShadowFacesToRender()
        
        updateLightShadowRanges()
    }

    private fun updateCollectionsIfLayoutChanged(cellSize: Int, maxFaceCount: Int)
    {
        if (lastResolution != resolution || lastCellSize != cellSize)
        {
            shadowBlocks.clear()
            shadowFaces.forEach { it.invalidate() }
            shadowFaceLastUpdatedFrameIndex = IntArray(maxFaceCount) { INITIAL_LAST_UPDATED_FRAME }
            lastResolution = resolution
            lastCellSize = cellSize
        }
        else if (shadowFaceLastUpdatedFrameIndex.size < maxFaceCount)
        {
            shadowFaceLastUpdatedFrameIndex = IntArray(maxFaceCount)
            {
                if (it < shadowFaceLastUpdatedFrameIndex.size) shadowFaceLastUpdatedFrameIndex[it] else INITIAL_LAST_UPDATED_FRAME 
            }
        }

        if (usedFaceSlots.size < maxFaceCount)
            usedFaceSlots = BooleanArray(maxFaceCount)
        else
            usedFaceSlots.fill(false)
    }

    private fun reserveCurrentLightBlocks(lights: DynamicList<RenderLight>, maxFaceCount: Int)
    {
        for (i in 0 until lights.size)
        {
            val light = lights[i]
            if (!light.shadowEnabled || light.shadowResolution <= 0)
                continue

            val faceCount = if (light.isSpotLight) 1 else POINT_LIGHT_SHADOW_FACE_COUNT
            val key = light.shadowBlockKey(i)
            val block = findExistingFreeBlock(key, faceCount, maxFaceCount) ?: continue

            markFaceSlotsUsed(block.faceOffset, block.faceCount)
            reservedBlocks += block
        }
    }

    private fun configureShadowFaces(lights: DynamicList<RenderLight>, maxFaceCount: Int, cellSize: Int, cameraPosition: Vector3fc?, tilesPerSide: Int) 
    {
        for (i in 0 until lights.size)
        {
            val light = lights[i]
            if (!light.shadowEnabled || light.shadowResolution <= 0)
                continue

            val faceCount = if (light.isSpotLight) 1 else POINT_LIGHT_SHADOW_FACE_COUNT
            val blockKey = light.shadowBlockKey(i)
            val reservedBlock = reservedBlocks.firstOrNull { it.key == blockKey && it.faceCount == faceCount }
            val block = reservedBlock ?: findOrAllocateBlock(blockKey, faceCount, maxFaceCount) ?: continue

            activeLights += light
            activeLightsFaceOffsets.add(block.faceOffset)
            activeLightsFaceCounts.add(block.faceCount)

            val faceSize = light.shadowResolution.coerceIn(64, cellSize)
            val updateImportance = lightShadowUpdateImportance(light, cameraPosition)

            if (light.isSpotLight)
            {
                val face = getOrCreateFace(block.faceOffset, faceSize, cellSize, tilesPerSide)
                val viewProjection = light.getSpotShadowViewProjection()
                face.configureFor(block, faceIndex = 0, faceSize, updateImportance, viewProjection)
            } 
            else
            {
                for (faceIndex in 0 until POINT_LIGHT_SHADOW_FACE_COUNT)
                {
                    val face = getOrCreateFace(block.faceOffset + faceIndex, faceSize, cellSize, tilesPerSide)
                    val viewProjection = light.getPointShadowViewProjection(faceIndex)
                    face.configureFor(block, faceIndex, faceSize, updateImportance, viewProjection)
                }
            }
        }
    }

    private fun selectShadowFacesToRender()
    {
        tmpCandidatesToRender.resetQuick()
        activeShadowFaces.forEach { tmpCandidatesToRender.add(it.index) }

        val numFacesToRender = min(maxShadowFacesPerFrame, tmpCandidatesToRender.size())
        if (numFacesToRender <= 0) return

        var selectedFaceCount = 0
        while (selectedFaceCount < numFacesToRender)
        {
            val bestCandidatePos = findBestShadowFaceCandidate()
            if (bestCandidatePos < 0) break

            val bestFaceIndex = tmpCandidatesToRender[bestCandidatePos]
            stageShadowFaceRender(bestFaceIndex, bestCandidatePos)
            selectedFaceCount++

            selectedFaceCount += selectSiblingShadowFacesToRender(
                face = shadowFaces[bestFaceIndex],
                maxCount = numFacesToRender - selectedFaceCount
            )
        }

        frameIndex++
    }

    private fun findBestShadowFaceCandidate(): Int
    {
        var bestCandidatePos = -1
        var bestFaceIndex    = -1
        var bestScore        = Float.NEGATIVE_INFINITY

        for (i in 0 until tmpCandidatesToRender.size())
        {
            val faceIndex = tmpCandidatesToRender[i]
            if (faceIndex < 0) continue // Candidate has been removed
            val score = faceRenderScore(faceIndex)
            val scoreCompare = score.compareTo(bestScore)

            if (bestCandidatePos < 0 || scoreCompare > 0 || (scoreCompare == 0 && faceIndex < bestFaceIndex))
            {
                bestCandidatePos = i
                bestFaceIndex = faceIndex
                bestScore = score
            }
        }

        return bestCandidatePos
    }

    private fun stageShadowFaceRender(faceIndex: Int, candidatePos: Int)
    {
        val face = shadowFaces[faceIndex]
        face.viewProjection.set(face.pendingViewProjection)
        face.valid = true
        shadowFaceIndicesToRender.add(faceIndex)
        shadowFaceLastUpdatedFrameIndex[faceIndex] = frameIndex
        tmpCandidatesToRender[candidatePos] = -1 // Mark candidate as removed
    }

    private fun selectSiblingShadowFacesToRender(face: ShadowFace, maxCount: Int): Int
    {
        if (maxCount <= 0 || face.faceCountInLight <= 1)
            return 0

        var selectedCount = 0
        val faceOffset = face.index - face.faceInLight
        for (siblingIndex in faceOffset until faceOffset + face.faceCountInLight)
        {
            if (selectedCount >= maxCount || siblingIndex == face.index)
                continue

            val candidatePos = tmpCandidatesToRender.indexOf(siblingIndex)
            if (candidatePos < 0)
                continue

            val sibling = shadowFaces[siblingIndex]
            if (sibling.blockKey != face.blockKey || sibling.faceCountInLight != face.faceCountInLight)
                continue

            stageShadowFaceRender(siblingIndex, candidatePos)
            selectedCount++
        }

        return selectedCount
    }

    private fun faceRenderScore(faceIndex: Int): Float
    {
        val face = shadowFaces[faceIndex]
        if (!face.valid)
        {
            return if (face.updateImportance >= USEFUL_SHADOW_IMPORTANCE)
                INVALID_FACE_UPDATE_SCORE + face.updateImportance
            else
                BACKGROUND_INVALID_FACE_UPDATE_SCORE + face.updateImportance
        }

        val framesSinceLastRender = frameIndex - shadowFaceLastUpdatedFrameIndex[faceIndex]
        val overdue = framesSinceLastRender.toFloat() / face.desiredUpdateInterval.toFloat()

        return when
        {
            framesSinceLastRender >= MAX_SHADOW_UPDATE_INTERVAL -> STARVED_FACE_UPDATE_SCORE + face.updateImportance + overdue
            overdue >= 1f -> OVERDUE_FACE_UPDATE_SCORE + face.updateImportance + overdue
            else -> BACKGROUND_FACE_UPDATE_SCORE + face.updateImportance * BACKGROUND_IMPORTANCE_WEIGHT + overdue
        }
    }

    private fun updateLightShadowRanges()
    {
        for (lightIndex in 0 until activeLights.size)
        {
            val faceOffset = activeLightsFaceOffsets[lightIndex]
            val faceCount = activeLightsFaceCounts[lightIndex]

            var allFacesValid = true
            for (i in 0 until faceCount)
            {
                if (!shadowFaces[faceOffset + i].valid)
                {
                    allFacesValid = false
                    break
                }
            }

            if (allFacesValid)
            {
                val light = activeLights[lightIndex]
                light.shadowFaceOffset = shadowFaces[faceOffset].bufferIndex
                light.shadowFaceCount = faceCount
            }
        }
    }

    private fun updateShadowFaceBufferIndices()
    {
        for (i in 0 until activeShadowFaces.size)
            activeShadowFaces[i].bufferIndex = i
    }

    private fun getOrCreateFace(index: Int, faceSize: Int, cellSize: Int, tilesPerSide: Int): ShadowFace
    {
        while (shadowFaces.size <= index)
            shadowFaces += ShadowFace()

        val face = shadowFaces[index]
        val oldSize = face.size
        val xTile = index % tilesPerSide
        val yTile = index / tilesPerSide

        face.index = index
        face.x = xTile * cellSize
        face.y = yTile * cellSize
        face.size = faceSize
        face.atlasScaleBias.set(
            faceSize.toFloat() / resolution.toFloat(),
            faceSize.toFloat() / resolution.toFloat(),
            face.x.toFloat() / resolution.toFloat(),
            face.y.toFloat() / resolution.toFloat()
        )

        if (oldSize != 0 && oldSize != faceSize)
            face.invalidate()

        return face
    }

    private fun findOrAllocateBlock(key: Long, faceCount: Int, maxFaceCount: Int): ShadowBlock?
    {
        val existingBlock = findExistingFreeBlock(key, faceCount, maxFaceCount)
        if (existingBlock != null)
            return existingBlock

        var freeOffset = 0
        while (freeOffset + faceCount <= maxFaceCount)
        {
            if (isFaceRangeFree(freeOffset, faceCount)) break
            freeOffset++
        }
        if (freeOffset + faceCount > maxFaceCount) return null

        val block = ShadowBlock(key, freeOffset, faceCount)
        
        markFaceSlotsUsed(block.faceOffset, block.faceCount)

        shadowBlocks.removeIf { it.key == key || it.overlaps(freeOffset, faceCount) }
        shadowBlocks += block
        
        return block
    }

    private fun findExistingFreeBlock(key: Long, faceCount: Int, maxFaceCount: Int): ShadowBlock? = 
        shadowBlocks.firstOrNull() 
        {
            it.key == key && 
            it.faceCount == faceCount &&
            it.faceOffset + faceCount <= maxFaceCount &&
            isFaceRangeFree(it.faceOffset, faceCount)
        }

    private fun isFaceRangeFree(offset: Int, faceCount: Int): Boolean
    {
        for (i in offset until offset + faceCount)
            if (usedFaceSlots[i]) return false
        
        return true
    }

    private fun markFaceSlotsUsed(offset: Int, faceCount: Int)
    {
        for (i in offset until offset + faceCount) usedFaceSlots[i] = true
    }

    private fun RenderLight.shadowBlockKey(submissionIndex: Int): Long =
        if (shadowId > 0L) shadowId else -(submissionIndex + 1L)

    private fun ShadowFace.configureFor(block: ShadowBlock, faceIndex: Int, faceSize: Int, updateImportance: Float, pendingViewProjection: Matrix4f)
    {
        val changedOwner = blockKey != block.key || faceInLight != faceIndex
        val changedType = faceCountInLight != block.faceCount
        val changedSize = size != 0 && size != faceSize

        if (changedOwner || changedType || changedSize)
        {
            invalidate()
            if (block.faceOffset + faceIndex < shadowFaceLastUpdatedFrameIndex.size)
                shadowFaceLastUpdatedFrameIndex[block.faceOffset + faceIndex] = INITIAL_LAST_UPDATED_FRAME
        }

        this.blockKey = block.key
        this.faceInLight = faceIndex
        this.faceCountInLight = block.faceCount
        this.updateImportance = updateImportance
        this.desiredUpdateInterval = shadowUpdateIntervalFor(updateImportance)
        this.pendingViewProjection.set(pendingViewProjection)

        activeShadowFaces += this
    }

    private fun lightShadowUpdateImportance(light: RenderLight, cameraPosition: Vector3fc?): Float
    {
        val manualImportance = max(0f, light.shadowImportance)
        val brightness = max(light.color.red, max(light.color.green, light.color.blue))
        val brightnessFactor = min(max(0.1f, brightness), 8f)
        val radius = max(1f, light.radius)
        val radiusFactor = min(max(0.5f, sqrt(radius) * 0.25f), 4f)
        var importance = manualImportance * brightnessFactor * radiusFactor

        if (cameraPosition == null)
            return max(MIN_SHADOW_UPDATE_IMPORTANCE, importance)

        val dx = light.position.x - cameraPosition.x()
        val dy = light.position.y - cameraPosition.y()
        val dz = light.position.z - cameraPosition.z()
        val distanceToLight = sqrt(dx * dx + dy * dy + dz * dz)
        val distanceToInfluence = max(1f, distanceToLight - radius)
        val distanceFactor = min(max(radius / (radius + distanceToInfluence), 0.05f), 1f)
        importance *= distanceFactor

        return max(MIN_SHADOW_UPDATE_IMPORTANCE, importance)
    }

    private fun shadowUpdateIntervalFor(importance: Float): Int = when
    {
        importance >= 12f -> 1 // Every frame
        importance >= 6f  -> 2 
        importance >= 3f  -> 4
        importance >= 1.5f -> 8
        importance >= 0.75f -> 16
        importance >= 0.35f -> 32
        importance >= USEFUL_SHADOW_IMPORTANCE -> 60
        else -> 120
    }

    private fun RenderLight.getSpotShadowViewProjection(): Matrix4f
    {
        val dir = this.direction
        val up = if (abs(dir.dot(WORLD_UP)) > 0.99f) WORLD_FORWARD else WORLD_UP
        tmpCenter.set(this.position).add(dir)

        val fov = min(max(this.outerConeAngle * 2f, 1f), 178f).toRadians()
        val near = 0.05f
        val far = max(this.radius, near + 0.01f)

        tmpView.identity().lookAt(this.position, tmpCenter, up)
        return tmpProjection.identity().perspective(fov, 1f, near, far).mul(tmpView)
    }

    private fun RenderLight.getPointShadowViewProjection(faceIndex: Int): Matrix4f
    {
        val dir = POINT_DIRECTIONS[faceIndex]
        val up = POINT_UPS[faceIndex]
        val near = 0.05f
        val far = max(this.radius, near + 0.01f)

        tmpCenter.set(this.position).add(dir)
        tmpView.identity().lookAt(this.position, tmpCenter, up)
        return tmpProjection.identity().perspective(90f.toRadians(), 1f, near, far).mul(tmpView)
    }

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
        var updateImportance = 0f
        var desiredUpdateInterval = 120
        var valid = false
        var blockKey = 0L
        var faceInLight = 0
        var faceCountInLight = 0

        fun invalidate()
        {
            valid = false
            bufferIndex = -1
            updateImportance = 0f
            desiredUpdateInterval = 120
            viewProjection.identity()
            pendingViewProjection.identity()
        }
    }

    private data class ShadowBlock(
        val key: Long,
        val faceOffset: Int,
        val faceCount: Int
    ) {
        fun overlaps(offset: Int, count: Int) = faceOffset < offset + count && offset < faceOffset + faceCount
    }

    companion object
    {
        const val POINT_LIGHT_SHADOW_FACE_COUNT = 6
        private const val USEFUL_SHADOW_IMPORTANCE = 0.1f
        private const val MIN_SHADOW_UPDATE_IMPORTANCE = 0.01f
        private const val BACKGROUND_IMPORTANCE_WEIGHT = 0.1f
        private const val MAX_SHADOW_UPDATE_INTERVAL = 240
        private const val INVALID_FACE_UPDATE_SCORE = 30_000f
        private const val STARVED_FACE_UPDATE_SCORE = 20_000f
        private const val OVERDUE_FACE_UPDATE_SCORE = 10_000f
        private const val BACKGROUND_INVALID_FACE_UPDATE_SCORE = 5_000f
        private const val BACKGROUND_FACE_UPDATE_SCORE = 0f
        private const val INITIAL_LAST_UPDATED_FRAME = -1000000

        private val WORLD_UP = Vector3f(0f, 1f, 0f)
        private val WORLD_FORWARD = Vector3f(0f, 0f, 1f)
        private val WORLD_BACKWARD = Vector3f(0f, 0f, 1f)
        private val WORLD_DOWN = Vector3f(0f, -1f, 0f)

        private val POINT_DIRECTIONS = arrayOf(
            Vector3f(1f, 0f, 0f),
            Vector3f(-1f, 0f, 0f),
            Vector3f(0f, 1f, 0f),
            Vector3f(0f, -1f, 0f),
            Vector3f(0f, 0f, 1f),
            Vector3f(0f, 0f, -1f)
        )

        private val POINT_UPS = arrayOf(
            WORLD_DOWN,
            WORLD_DOWN,
            WORLD_FORWARD,
            WORLD_BACKWARD,
            WORLD_DOWN,
            WORLD_DOWN
        )
    }
}