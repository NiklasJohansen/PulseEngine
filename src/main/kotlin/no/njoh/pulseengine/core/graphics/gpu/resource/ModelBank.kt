package no.njoh.pulseengine.core.graphics.gpu.resource

import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.gpu.buffer.DoubleBufferedIntObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.StaticBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.VertexArrayObject
import no.njoh.pulseengine.core.graphics.gpu.shader.VertexAttributeLayout
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_SHORT
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT
import org.lwjgl.opengl.GL30.GL_HALF_FLOAT
import org.lwjgl.opengl.GL32.GL_ALREADY_SIGNALED
import org.lwjgl.opengl.GL32.GL_CONDITION_SATISFIED
import org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE
import org.lwjgl.opengl.GL32.GL_WAIT_FAILED
import org.lwjgl.opengl.GL32.glClientWaitSync
import org.lwjgl.opengl.GL32.glDeleteSync
import org.lwjgl.opengl.GL32.glFenceSync
import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * Owns model geometry and mesh metadata after upload.
 *
 * Deletion is deferred because the render scene is double-buffered: a model can be unloaded while
 * the previous scene still references its meshes. A fence delays deletion until after their last
 * possible draw, at which point their metadata slots can safely be reused by later model uploads.
 */
class ModelBank
{
    private var metadataBuffer: DoubleBufferedIntObject? = null
    private var skinningBoundsBuffer: DoubleBufferedIntObject? = null
    private val meshMetadata = ArrayList<MeshMetadata?>(128)
    private val skinningBoundsRecords = ArrayList<SkinningBoundsRecord?>(512)
    private val freeMetadataIndices = ArrayDeque<Int>(128)
    private val freeSkinningBoundsRanges = ArrayList<FreeRange>(16)
    private val modelAllocations = IdentityHashMap<Model, ModelAllocation>()
    private val pendingDeletions = ArrayList<ModelAllocation>()
    private val deletionBatches = ArrayList<DeferredDeletionBatch>()
    private var metadataDirty = false

    fun upload(model: Model)
    {
        if ((model.vbo == null && model.vertexBytes.isEmpty()) || (model.ebo == null && model.indices.isEmpty()))
        {
            Logger.warn { "Attempted to upload empty mesh to GPU: ${model.name} (${model.filePath})" }
            return
        }

        val gpuMesh = uploadMesh(model)

        val previousAllocation = modelAllocations[model]
        if (previousAllocation != null)
        {
            val prevGpuMesh = previousAllocation.gpuMesh
            check(prevGpuMesh.vbo === gpuMesh.vbo && prevGpuMesh.ebo === gpuMesh.ebo) { "A live model allocation changed geometry buffers without being deleted" }
            prevGpuMesh.vao.destroy()
            prevGpuMesh.vao = gpuMesh.vao
            return
        }

        val metadata = uploadMetadata(model)
        modelAllocations[model] = ModelAllocation(
            gpuMesh = gpuMesh,
            metadataIndices = metadata.metadataIndices,
            skinningBoundsOffset = metadata.skinningBoundsOffset,
            skinningBoundsCount = metadata.skinningBoundsCount
        )
    }

    fun delete(model: Model)
    {
        // Detach the asset immediately, but retain the allocation for already queued RenderItems this frame
        modelAllocations.remove(model)?.let { pendingDeletions += it }
        model.onDeleted()
    }

    fun initFrame()
    {
        // Only release batches whose end-of-frame fence proves all preceding draws have completed
        var i = 0
        while (i < deletionBatches.size)
        {
            val batch = deletionBatches[i]
            when (glClientWaitSync(batch.fence, 0, 0L))
            {
                GL_ALREADY_SIGNALED, GL_CONDITION_SATISFIED ->
                {
                    glDeleteSync(batch.fence)
                    batch.allocations.forEachFast { release(it) }
                    deletionBatches.removeAt(i)
                }
                GL_WAIT_FAILED ->
                {
                    if (!batch.waitFailureLogged)
                    {
                        Logger.warn { "Failed waiting for queued model resources; keeping them to avoid unsafe deletion" }
                        batch.waitFailureLogged = true
                    }
                    i++
                }
                else -> i++
            }
        }
    }

    fun endFrame()
    {
        if (pendingDeletions.isEmpty()) return

        deletionBatches += DeferredDeletionBatch(
            fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0),
            allocations = ArrayList(pendingDeletions)
        )
        pendingDeletions.clear()
    }

    fun submitAndBind()
    {
        val buffer = metadataBuffer ?: return

        if (metadataDirty)
        {
            for (metadata in meshMetadata)
                buffer.writeRecord(metadata)

            buffer.swapBuffers()
            buffer.bind()
            buffer.submit()
            buffer.release()

            skinningBoundsBuffer?.let()
            {
                for (record in skinningBoundsRecords)
                    it.writeRecord(record)

                it.swapBuffers()
                it.bind()
                it.submit()
                it.release()
            }

            metadataDirty = false
        }
        else
        {
            buffer.bind()
            buffer.release()
            skinningBoundsBuffer?.bind()
            skinningBoundsBuffer?.release()
        }
    }

    fun destroy()
    {
        modelAllocations.forEach { (model, allocation) ->
            model.onDeleted()
            allocation.gpuMesh.destroy()
        }

        pendingDeletions.forEachFast { it.gpuMesh.destroy() }

        deletionBatches.forEachFast { batch ->
            glDeleteSync(batch.fence)
            batch.allocations.forEachFast { it.gpuMesh.destroy() }
        }

        modelAllocations.clear()
        pendingDeletions.clear()
        deletionBatches.clear()
        meshMetadata.clear()
        skinningBoundsRecords.clear()
        freeMetadataIndices.clear()
        freeSkinningBoundsRanges.clear()

        metadataBuffer?.destroy()
        skinningBoundsBuffer?.destroy()
        metadataBuffer = null
        skinningBoundsBuffer = null
        metadataDirty = false
    }

    private fun uploadMesh(model: Model): GpuMesh
    {
        // Always recreate VAO as this is destroyed when the window is recreated
        val vao = VertexArrayObject.createAndBind()

        // VBO and EBO survive window recreation, so keep them if they already exist
        val vbo = model.vbo ?: StaticBufferObject.createArrayBuffer(model.vertexBytes)
        vbo.bind()

        VertexAttributeLayout().apply()
        {
            withAttribute("position", 3, GL_FLOAT, location = 0)

            if (model.hasNormals)
                withAttribute("normal", 3, GL_SHORT, normalized = true, location = 1)

            if (model.hasTangents)
                withAttribute("tangent", 4, GL_SHORT, normalized = true, location = 2)

            if (model.hasTexCoords)
                withAttribute("texCoord", 2, GL_HALF_FLOAT, location = 3)

            if (model.hasBones)
            {
                withAttribute("boneIndices", 4, GL_UNSIGNED_SHORT, integer = true, location = 4)
                withAttribute("boneWeights", 4, GL_UNSIGNED_BYTE, normalized = true, location = 5)
            }
            alignStride(4)
        }.bind()

        val ebo = model.ebo ?: StaticBufferObject.createElementArrayBuffer(model.indices)
        ebo.bind()

        vao.release()
        vbo.release()
        ebo.release()
        model.onUploaded(vao, vbo, ebo)

        return GpuMesh(vao, vbo, ebo)
    }

    private fun uploadMetadata(model: Model): ModelMetadataAllocation
    {
        ensureMetadataBuffer()

        val skinningBoundsCount = model.meshes.sumOf { it.skinningBounds?.boneIndices?.size ?: 0 }
        val skinningBoundsOffset = allocateSkinningBounds(skinningBoundsCount)
        var nextSkinningBoundsOffset = skinningBoundsOffset
        val metadataIndices = IntArray(model.meshes.size)

        for (i in 0 until model.meshes.size)
        {
            val mesh = model.meshes[i]
            val meshSkinningBoundsCount = mesh.skinningBounds?.boneIndices?.size ?: 0
            val meshSkinningBoundsOffset = if (meshSkinningBoundsCount == 0) NO_SKINNING_BOUNDS_OFFSET else nextSkinningBoundsOffset
            writeSkinningBounds(mesh.skinningBounds, meshSkinningBoundsOffset)
            nextSkinningBoundsOffset += meshSkinningBoundsCount

            val metadataIndex = allocateMetadataIndex()
            metadataIndices[i] = metadataIndex
            mesh.assignGpuMetadataIndex(metadataIndex)
            meshMetadata[metadataIndex] = MeshMetadata.from(mesh, meshSkinningBoundsOffset)
        }

        metadataDirty = true

        return ModelMetadataAllocation(metadataIndices, skinningBoundsOffset, skinningBoundsCount)
    }

    private fun ensureMetadataBuffer()
    {
        if (metadataBuffer != null) return

        metadataDirty = true
        metadataBuffer = DoubleBufferedIntObject.createShaderStorageBuffer(
            blockBinding = METADATA_BUFFER_BINDING,
            initCapacity = RECORD_INTS * 128
        )
    }

    private fun ensureSkinningBoundsBuffer()
    {
        if (skinningBoundsBuffer != null || skinningBoundsRecords.isEmpty()) return

        metadataDirty = true
        skinningBoundsBuffer = DoubleBufferedIntObject.createShaderStorageBuffer(
            blockBinding = SKINNING_BOUNDS_BUFFER_BINDING,
            initCapacity = SKINNING_BOUNDS_RECORD_INTS * 512
        )
    }

    private fun allocateMetadataIndex(): Int
    {
        // Reuse freed slots so repeated hot reloads do not grow the metadata buffer indefinitely.
        if (freeMetadataIndices.isNotEmpty())
            return freeMetadataIndices.removeFirst()

        val index = meshMetadata.size
        meshMetadata += null
        return index
    }

    private fun allocateSkinningBounds(count: Int): Int
    {
        if (count == 0) return NO_SKINNING_BOUNDS_OFFSET

        // Mesh metadata stores one offset, so its skinning records must be contiguous.
        for (i in freeSkinningBoundsRanges.indices)
        {
            val range = freeSkinningBoundsRanges[i]
            if (range.count < count) continue

            val offset = range.offset
            if (range.count == count)
            {
                freeSkinningBoundsRanges.removeAt(i)
            }
            else
            {
                range.offset += count
                range.count -= count
            }
            return offset
        }

        val offset = skinningBoundsRecords.size
        repeat(count) { skinningBoundsRecords += null }
        ensureSkinningBoundsBuffer()
        return offset
    }

    private fun writeSkinningBounds(skinningBounds: Model.SkinningBounds?, offset: Int)
    {
        if (skinningBounds == null || offset == NO_SKINNING_BOUNDS_OFFSET) return

        val restPoseBounds = skinningBounds.restPoseBounds
        for (i in skinningBounds.boneIndices.indices)
        {
            val boundsOffset = i * 6
            skinningBoundsRecords[offset + i] = SkinningBoundsRecord(
                boneIndex = skinningBounds.boneIndices[i],
                xMin = restPoseBounds[boundsOffset],
                yMin = restPoseBounds[boundsOffset + 1],
                zMin = restPoseBounds[boundsOffset + 2],
                xMax = restPoseBounds[boundsOffset + 3],
                yMax = restPoseBounds[boundsOffset + 4],
                zMax = restPoseBounds[boundsOffset + 5]
            )
        }
    }

    private fun release(allocation: ModelAllocation)
    {
        allocation.gpuMesh.destroy()
        allocation.metadataIndices.forEach()
        {
            meshMetadata[it] = null
            freeMetadataIndices += it
        }
        freeSkinningBounds(allocation.skinningBoundsOffset, allocation.skinningBoundsCount)
        metadataDirty = true
    }

    private fun freeSkinningBounds(offset: Int, count: Int)
    {
        if (offset == NO_SKINNING_BOUNDS_OFFSET || count == 0) return

        // Clear the released records so a later metadata upload cannot expose stale bounds
        for (i in offset until offset + count)
            skinningBoundsRecords[i] = null

        // Keep ranges ordered by offset so adjacent free blocks can be found in one pass
        var insertionIndex = 0
        while (insertionIndex < freeSkinningBoundsRanges.size && freeSkinningBoundsRanges[insertionIndex].offset < offset)
            insertionIndex++

        freeSkinningBoundsRanges.add(insertionIndex, FreeRange(offset, count))

        // Merge touching ranges so a larger model can reuse them as one contiguous block
        var i = 0
        while (i < freeSkinningBoundsRanges.size - 1)
        {
            val current = freeSkinningBoundsRanges[i]
            val next = freeSkinningBoundsRanges[i + 1]
            if (current.offset + current.count >= next.offset)
            {
                current.count = maxOf(current.offset + current.count, next.offset + next.count) - current.offset
                freeSkinningBoundsRanges.removeAt(i + 1)
            }
            else i++
        }

        // A free range at the end needs no tracking, remove it from the record list instead
        while (freeSkinningBoundsRanges.isNotEmpty())
        {
            val last = freeSkinningBoundsRanges.last()
            if (last.offset + last.count != skinningBoundsRecords.size) break

            repeat(last.count) { skinningBoundsRecords.removeLast() }
            freeSkinningBoundsRanges.removeLast()
        }
    }

    private fun DoubleBufferedIntObject.writeRecord(metadata: MeshMetadata?)
    {
        fill(RECORD_INTS)
        {
            if (metadata == null)
            {
                put(0, 0, 0, 0)
                put(0, 0, 0, 0)
                put(0, 0, 0, 0)
                put(0, 0, 0, 0)
            }
            else
            {
                put(metadata.indexCount, metadata.indexStart, metadata.baseVertex, 0)
                put(metadata.xCenter.toRawBits(), metadata.yCenter.toRawBits(), metadata.zCenter.toRawBits(), metadata.xHalf.toRawBits())
                put(metadata.yHalf.toRawBits(), metadata.zHalf.toRawBits(), 0f.toRawBits(), 0f.toRawBits())
                put(metadata.skinningBoundsOffset, metadata.skinningBoundsCount, if (metadata.includeBaseBoundsInSkinning) 1 else 0, 0)
            }
        }
    }

    private fun DoubleBufferedIntObject.writeRecord(metadata: SkinningBoundsRecord?)
    {
        fill(SKINNING_BOUNDS_RECORD_INTS)
        {
            if (metadata == null)
            {
                put(0, 0, 0, 0)
                put(0, 0, 0, 0)
            }
            else
            {
                put(metadata.xMin.toRawBits(), metadata.yMin.toRawBits(), metadata.zMin.toRawBits(), metadata.xMax.toRawBits())
                put(metadata.yMax.toRawBits(), metadata.zMax.toRawBits(), metadata.boneIndex, 0)
            }
        }
    }

    private data class GpuMesh(
        var vao: VertexArrayObject,
        val vbo: StaticBufferObject,
        val ebo: StaticBufferObject
    ) {
        fun destroy()
        {
            vao.destroy()
            vbo.destroy()
            ebo.destroy()
        }
    }

    private data class ModelMetadataAllocation(
        val metadataIndices: IntArray,
        val skinningBoundsOffset: Int,
        val skinningBoundsCount: Int
    )

    private data class ModelAllocation(
        val gpuMesh: GpuMesh,
        val metadataIndices: IntArray,
        val skinningBoundsOffset: Int,
        val skinningBoundsCount: Int
    )

    private data class DeferredDeletionBatch(
        val fence: Long,
        val allocations: ArrayList<ModelAllocation>,
        var waitFailureLogged: Boolean = false
    )

    private data class FreeRange(var offset: Int, var count: Int)

    private data class MeshMetadata(
        val indexCount: Int,
        val indexStart: Int,
        val baseVertex: Int,
        val xCenter: Float,
        val yCenter: Float,
        val zCenter: Float,
        val xHalf: Float,
        val yHalf: Float,
        val zHalf: Float,
        val skinningBoundsOffset: Int,
        val skinningBoundsCount: Int,
        val includeBaseBoundsInSkinning: Boolean
    ) {
        companion object
        {
            fun from(mesh: Mesh, skinningBoundsOffset: Int): MeshMetadata
            {
                val staticBounds = mesh.skinningBounds?.staticBounds
                val hasGpuSkinningBounds = skinningBoundsOffset != NO_SKINNING_BOUNDS_OFFSET
                val baseBounds = when
                {
                    hasGpuSkinningBounds && staticBounds != null -> staticBounds
                    hasGpuSkinningBounds -> null
                    else -> mesh.animatedBounds ?: mesh.localBounds
                }

                return MeshMetadata(
                    indexCount = mesh.indexCount,
                    indexStart = mesh.indexStart,
                    baseVertex = 0,
                    xCenter = baseBounds?.let { (it.xMin + it.xMax) * 0.5f } ?: 0f,
                    yCenter = baseBounds?.let { (it.yMin + it.yMax) * 0.5f } ?: 0f,
                    zCenter = baseBounds?.let { (it.zMin + it.zMax) * 0.5f } ?: 0f,
                    xHalf   = baseBounds?.let { (it.xMax - it.xMin) * 0.5f } ?: 0f,
                    yHalf   = baseBounds?.let { (it.yMax - it.yMin) * 0.5f } ?: 0f,
                    zHalf   = baseBounds?.let { (it.zMax - it.zMin) * 0.5f } ?: 0f,
                    skinningBoundsOffset = skinningBoundsOffset,
                    skinningBoundsCount = mesh.skinningBounds?.boneIndices?.size ?: 0,
                    includeBaseBoundsInSkinning = hasGpuSkinningBounds && staticBounds != null
                )
            }
        }
    }

    private data class SkinningBoundsRecord(
        val boneIndex: Int,
        val xMin: Float,
        val yMin: Float,
        val zMin: Float,
        val xMax: Float,
        val yMax: Float,
        val zMax: Float
    )

    companion object
    {
        const val METADATA_BUFFER_BINDING = 7
        const val SKINNING_BOUNDS_BUFFER_BINDING = 12
        private const val RECORD_INTS = 16
        private const val SKINNING_BOUNDS_RECORD_INTS = 8
        private const val NO_SKINNING_BOUNDS_OFFSET = -1
    }
}