package no.njoh.pulseengine.core.graphics.api.world

import gnu.trove.list.array.TIntArrayList
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.primitives.DynamicList
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
    var tileResolution = 512
    var maxFacesPerFrame = 3

    private val faces = ArrayList<LocalShadowFace>(64)
    private val shadowBlocks = ArrayList<ShadowBlock>(64)
    private val reservedBlocks = ArrayList<ShadowBlock>(64)
    private val activeLights = ArrayList<WorldRenderLight>(64)
    private var usedFaceSlots = BooleanArray(0)
    private var faceLastUpdatedFrameIndex = IntArray(0)
    private val activeLightsFaceOffsets = TIntArrayList(64)
    private val activeLightsFaceCounts = TIntArrayList(64)
    private val faceIndicesToUpdate = TIntArrayList(64)
    private val candidatesFaceIndicesToUpdate = TIntArrayList(64)
    private var activeFaceCount = 0
    private var frameIndex = 0
    private var lastResolution = 0
    private var lastCellSize = 0

    private val tmpView = Matrix4f()
    private val tmpProjection = Matrix4f()
    private val tmpCenter = Vector3f()

    fun prepare(scene: WorldRenderScene, cameraPosition: Vector3fc? = null)
    {
        val lights = scene.localLights
        activeLights.clear()
        reservedBlocks.clear()
        activeLightsFaceOffsets.resetQuick()
        activeLightsFaceCounts.resetQuick()
        faceIndicesToUpdate.resetQuick()
        candidatesFaceIndicesToUpdate.resetQuick()
        activeFaceCount = 0

        for (i in 0 until lights.size)
        {
            lights[i].shadowFaceOffset = -1
            lights[i].shadowFaceCount = 0
        }

        if (!enabled || lights.isEmpty()) 
            return


        val cellSize = tileResolution.coerceIn(64, max(64, resolution))
        val tilesPerSide = max(1, resolution / cellSize)
        val maxFaceCount = tilesPerSide * tilesPerSide

        updateCollectionsIfLayoutChanged(cellSize, maxFaceCount)

        reserveCurrentLightBlocks(lights, maxFaceCount)

        configureShadowFaces(lights, maxFaceCount, cellSize, cameraPosition, tilesPerSide)

        selectShadowFacesToUpdate()
        
        updateLightShadowRanges()
    }

    private fun updateCollectionsIfLayoutChanged(cellSize: Int, maxFaceCount: Int)
    {
        if (lastResolution != resolution || lastCellSize != cellSize)
        {
            shadowBlocks.clear()
            faceLastUpdatedFrameIndex = IntArray(maxFaceCount) { INITIAL_LAST_UPDATED_FRAME }

            for (face in faces) face.invalidate()

            lastResolution = resolution
            lastCellSize = cellSize
        }
        else if (faceLastUpdatedFrameIndex.size < maxFaceCount)
        {
            val previous = faceLastUpdatedFrameIndex
            faceLastUpdatedFrameIndex = IntArray(maxFaceCount) { INITIAL_LAST_UPDATED_FRAME }
            for (i in 0 until previous.size)
                faceLastUpdatedFrameIndex[i] = previous[i]
        }

        if (usedFaceSlots.size < maxFaceCount)
            usedFaceSlots = BooleanArray(maxFaceCount)
        else
            usedFaceSlots.fill(false, 0, maxFaceCount)
    }

    private fun reserveCurrentLightBlocks(lights: DynamicList<WorldRenderLight>, maxFaceCount: Int)
    {
        for (i in 0 until lights.size)
        {
            val light = lights[i]
            if (!light.shadowEnabled || light.shadowResolution <= 0)
                continue

            val faceCount = if (light.isSpotLight) 1 else POINT_FACE_COUNT
            val key = light.shadowBlockKey(i)
            val block = findExistingFreeBlock(key, faceCount, maxFaceCount) ?: continue

            markFaceSlotsUsed(block.faceOffset, block.faceCount)
            reservedBlocks += block
        }
    }

    private fun configureShadowFaces(lights: DynamicList<WorldRenderLight>, maxFaceCount: Int, cellSize: Int, cameraPosition: Vector3fc?, tilesPerSide: Int) 
    {
        for (i in 0 until faces.size)
            faces[i].active = false

        for (i in 0 until lights.size)
        {
            val light = lights[i]
            if (!light.shadowEnabled || light.shadowResolution <= 0)
                continue

            val faceCount = if (light.isSpotLight) 1 else POINT_FACE_COUNT
            val blockKey = light.shadowBlockKey(i)
            val reservedBlock = findReservedBlock(blockKey, faceCount)
            val block = reservedBlock ?: findOrAllocateBlock(blockKey, faceCount, maxFaceCount) ?: continue

            if (reservedBlock == null)
                markFaceSlotsUsed(block.faceOffset, block.faceCount)

            activeLights += light
            activeLightsFaceOffsets.add(block.faceOffset)
            activeLightsFaceCounts.add(block.faceCount)
            activeFaceCount = max(activeFaceCount, block.faceOffset + block.faceCount)

            val faceSize = light.shadowResolution.coerceIn(64, cellSize)
            val updatePriority = lightUpdatePriority(light, cameraPosition)

            if (light.isSpotLight)
            {
                val face = getOrCreateFace(block.faceOffset, faceSize, cellSize, tilesPerSide)
                val viewProjection = light.getSpotShadowViewProjection()
                face.configureFor(block, faceIndex = 0, faceSize, updatePriority, viewProjection)
            } 
            else
            {
                for (faceIndex in 0 until POINT_FACE_COUNT)
                {
                    val face = getOrCreateFace(block.faceOffset + faceIndex, faceSize, cellSize, tilesPerSide)
                    val viewProjection = light.getPointShadowViewProjection(faceIndex)
                    face.configureFor(block, faceIndex, faceSize, updatePriority, viewProjection)
                }
            }
        }
    }

    private fun selectShadowFacesToUpdate()
    {
        for (i in 0 until activeFaceCount)
        {
            if (faces[i].active) candidatesFaceIndicesToUpdate.add(i)
        }

        val numFacesToUpdate = min(maxFacesPerFrame, candidatesFaceIndicesToUpdate.size())
        if (numFacesToUpdate <= 0) return

        sortCandidateFacesToUpdate(candidatesFaceIndicesToUpdate.size())

        for (i in 0 until numFacesToUpdate)
        {
            val faceIndex = candidatesFaceIndicesToUpdate[i]
            val face = faces[faceIndex]
            face.viewProjection.set(face.pendingViewProjection)
            face.valid = true
            faceIndicesToUpdate.add(faceIndex)
            faceLastUpdatedFrameIndex[faceIndex] = frameIndex
        }

        frameIndex++
    }

    private fun updateLightShadowRanges()
    {
        for (assignmentIndex in 0 until activeLights.size)
        {
            val faceOffset = activeLightsFaceOffsets[assignmentIndex]
            val faceCount = activeLightsFaceCounts[assignmentIndex]
            var allFacesValid = true
            for (i in 0 until faceCount)
            {
                if (!faces[faceOffset + i].valid)
                {
                    allFacesValid = false
                    break
                }
            }

            if (allFacesValid)
            {
                val light = activeLights[assignmentIndex]
                light.shadowFaceOffset = faceOffset
                light.shadowFaceCount = faceCount
            }
        }
    }
    
    private fun compareFaceUpdatePriority(a: Int, b: Int): Int
    {
        val result = faceUpdateScore(b).compareTo(faceUpdateScore(a))
        return if (result != 0) result else a - b
    }

    private fun sortCandidateFacesToUpdate(count: Int)
    {
        for (i in 1 until count)
        {
            val value = candidatesFaceIndicesToUpdate[i]
            var j = i - 1
            while (j >= 0 && compareFaceUpdatePriority(candidatesFaceIndicesToUpdate[j], value) > 0)
            {
                candidatesFaceIndicesToUpdate[j + 1] = candidatesFaceIndicesToUpdate[j]
                j--
            }
            candidatesFaceIndicesToUpdate[j + 1] = value
        }
    }

    private fun faceUpdateScore(faceIndex: Int): Float
    {
        val face = faces[faceIndex]
        if (!face.valid)
            return INVALID_FACE_UPDATE_SCORE + face.updatePriority * PRIORITY_WEIGHT

        val framesSinceUpdate = frameIndex - faceLastUpdatedFrameIndex[faceIndex]
        return face.updatePriority * PRIORITY_WEIGHT + framesSinceUpdate
    }

    private fun getOrCreateFace(index: Int, faceSize: Int, cellSize: Int, tilesPerSide: Int): LocalShadowFace
    {
        while (faces.size <= index)
            faces += LocalShadowFace()

        val face = faces[index]
        val oldSize = face.size
        val xTile = index % tilesPerSide
        val yTile = index / tilesPerSide

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

        val offset = findFreeFaceRange(faceCount, maxFaceCount) ?: return null
        removeShadowBlocks(key, offset, faceCount)

        return ShadowBlock(key, offset, faceCount).also { shadowBlocks += it }
    }

    private fun findReservedBlock(key: Long, faceCount: Int): ShadowBlock?
    {
        for (i in 0 until reservedBlocks.size)
        {
            val block = reservedBlocks[i]
            if (block.key == key && block.faceCount == faceCount)
                return block
        }
        return null
    }

    private fun findExistingFreeBlock(key: Long, faceCount: Int, maxFaceCount: Int): ShadowBlock?
    {
        for (i in 0 until shadowBlocks.size)
        {
            val block = shadowBlocks[i]
            if (block.key == key &&
                block.faceCount == faceCount &&
                block.faceOffset + faceCount <= maxFaceCount &&
                isFaceRangeFree(block.faceOffset, faceCount)
            ) {
                return block
            }
        }
        return null
    }

    private fun removeShadowBlocks(key: Long, offset: Int, faceCount: Int)
    {
        var index = 0
        while (index < shadowBlocks.size)
        {
            val block = shadowBlocks[index]
            if (block.key == key || block.overlaps(offset, faceCount))
                shadowBlocks.removeAt(index)
            else index++
        }
    }

    private fun findFreeFaceRange(faceCount: Int, maxFaceCount: Int): Int?
    {
        var offset = 0
        while (offset + faceCount <= maxFaceCount)
        {
            if (isFaceRangeFree(offset, faceCount))
                return offset
            offset++
        }
        return null
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

    private fun WorldRenderLight.shadowBlockKey(submissionIndex: Int): Long =
        if (shadowId > 0L) shadowId else -(submissionIndex + 1L)

    private fun LocalShadowFace.configureFor(block: ShadowBlock, faceIndex: Int, faceSize: Int, updatePriority: Float, pendingViewProjection: Matrix4f)
    {
        val changedOwner = blockKey != block.key || faceInLight != faceIndex
        val changedType = faceCountInLight != block.faceCount
        val changedSize = size != 0 && size != faceSize

        if (changedOwner || changedType || changedSize)
        {
            invalidate()
            if (block.faceOffset + faceIndex < faceLastUpdatedFrameIndex.size)
                faceLastUpdatedFrameIndex[block.faceOffset + faceIndex] = INITIAL_LAST_UPDATED_FRAME
        }

        this.active = true
        this.blockKey = block.key
        this.faceInLight = faceIndex
        this.faceCountInLight = block.faceCount
        this.updatePriority = updatePriority
        this.pendingViewProjection.set(pendingViewProjection)
    }

    private fun lightUpdatePriority(light: WorldRenderLight, cameraPosition: Vector3fc?): Float
    {
        val importance = max(0f, light.shadowImportance)
        if (cameraPosition == null)
            return importance

        val dx = light.position.x - cameraPosition.x()
        val dy = light.position.y - cameraPosition.y()
        val dz = light.position.z - cameraPosition.z()
        val distanceToLight = sqrt(dx * dx + dy * dy + dz * dz)
        val distanceToInfluence = max(1f, distanceToLight - light.radius)
        return importance / distanceToInfluence
    }

    private fun WorldRenderLight.getSpotShadowViewProjection(): Matrix4f
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

    private fun WorldRenderLight.getPointShadowViewProjection(faceIndex: Int): Matrix4f
    {
        val dir = POINT_DIRECTIONS[faceIndex]
        val up = POINT_UPS[faceIndex]
        val near = 0.05f
        val far = max(this.radius, near + 0.01f)

        tmpCenter.set(this.position).add(dir)
        tmpView.identity().lookAt(this.position, tmpCenter, up)
        return tmpProjection.identity().perspective(90f.toRadians(), 1f, near, far).mul(tmpView)
    }

    fun getFaceCount() = activeFaceCount

    fun getFace(index: Int) = faces[index]

    fun getUpdateFaceCount() = faceIndicesToUpdate.size()

    fun getUpdateFaceIndex(index: Int) = faceIndicesToUpdate[index]
    
    class LocalShadowFace
    {
        val viewProjection = Matrix4f()
        val pendingViewProjection = Matrix4f()
        val atlasScaleBias = Vector4f()
        var x = 0
        var y = 0
        var size = 0
        var updatePriority = 0f
        var active = false
        var valid = false
        var blockKey = 0L
        var faceInLight = 0
        var faceCountInLight = 0

        fun invalidate()
        {
            valid = false
            updatePriority = 0f
            viewProjection.identity()
            pendingViewProjection.identity()
        }
    }

    private data class ShadowBlock(
        val key: Long,
        val faceOffset: Int,
        val faceCount: Int
    ) {
        fun overlaps(offset: Int, count: Int) =
            faceOffset < offset + count && offset < faceOffset + faceCount
    }

    companion object
    {
        const val POINT_FACE_COUNT = 6
        private const val PRIORITY_WEIGHT = 1000f
        private const val INVALID_FACE_UPDATE_SCORE = 1_000_000_000f
        private const val INITIAL_LAST_UPDATED_FRAME = -1000000

        private val WORLD_UP = Vector3f(0f, 1f, 0f)
        private val WORLD_FORWARD = Vector3f(0f, 0f, 1f)
        private val WORLD_BACKWARD = Vector3f(0f, 0f, 1f)
        private val WORLD_DOWN = Vector3f(0f, -1f, 0f)

        private val POINT_DIRECTIONS = arrayOf(
            Vector3f( 1f,  0f,  0f),
            Vector3f(-1f,  0f,  0f),
            Vector3f( 0f,  1f,  0f),
            Vector3f( 0f, -1f,  0f),
            Vector3f( 0f,  0f,  1f),
            Vector3f( 0f,  0f, -1f)
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
