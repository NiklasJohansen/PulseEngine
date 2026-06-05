package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.api.objects.DoubleBufferedIntObject
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_SHORT
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT
import org.lwjgl.opengl.GL30.GL_HALF_FLOAT

class ModelBank
{
    private var metadataBuffer: DoubleBufferedIntObject? = null
    private var skinningBoundsBuffer: DoubleBufferedIntObject? = null
    private val meshMetadata = ArrayList<MeshMetadata>(128)
    private val skinningBoundsRecords = ArrayList<SkinningBoundsRecord>(512)
    private var metadataDirty = false

    fun upload(model: Model)
    {
        if ((model.vbo == null && model.vertexBytes.isEmpty()) || (model.ebo == null && model.indices.isEmpty()))
        {
            Logger.warn { "Attempted to upload empty mesh to GPU: ${model.name} (${model.filePath})" }
            return
        }

        val wasUploaded = uploadMesh(model)
        if (wasUploaded)
            uploadMetadata(model)
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
        metadataBuffer?.destroy()
        skinningBoundsBuffer?.destroy()
        metadataBuffer = null
        skinningBoundsBuffer = null
        meshMetadata.clear()
        skinningBoundsRecords.clear()
        metadataDirty = false
    }

    private fun uploadMesh(model: Model): Boolean
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

        val wasUploaded = (vbo !== model.vbo)

        vao.release()
        vbo.release()
        ebo.release()
        model.onUploaded(vao, vbo, ebo)
        
        return wasUploaded
    }

    private fun uploadMetadata(model: Model)
    {
        ensureMetadataBuffer()

        for (mesh in model.meshes)
        {
            val skinningBoundsOffset = appendSkinningBounds(mesh.skinningBounds)
            val metadata = MeshMetadata.from(mesh, skinningBoundsOffset)
            val index = mesh.gpuMetadataIndex

            if (index in meshMetadata.indices)
            {
                meshMetadata[index] = metadata
            }
            else
            {
                mesh.gpuMetadataIndex = meshMetadata.size
                meshMetadata += metadata
            }
        }

        metadataDirty = true
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

    private fun appendSkinningBounds(skinningBounds: Model.SkinningBounds?): Int
    {
        if (skinningBounds == null || skinningBounds.boneIndices.isEmpty())
            return NO_SKINNING_BOUNDS_OFFSET

        val offset = skinningBoundsRecords.size
        val restPoseBounds = skinningBounds.restPoseBounds
        for (i in skinningBounds.boneIndices.indices)
        {
            val boundsOffset = i * 6
            skinningBoundsRecords += SkinningBoundsRecord(
                boneIndex = skinningBounds.boneIndices[i],
                xMin = restPoseBounds[boundsOffset],
                yMin = restPoseBounds[boundsOffset + 1],
                zMin = restPoseBounds[boundsOffset + 2],
                xMax = restPoseBounds[boundsOffset + 3],
                yMax = restPoseBounds[boundsOffset + 4],
                zMax = restPoseBounds[boundsOffset + 5]
            )
        }
        ensureSkinningBoundsBuffer()
        return offset
    }

    private fun DoubleBufferedIntObject.writeRecord(metadata: MeshMetadata)
    {
        fill(RECORD_INTS)
        {
            put(metadata.indexCount, metadata.indexStart, metadata.baseVertex, 0)
            put(metadata.xCenter.toRawBits(), metadata.yCenter.toRawBits(), metadata.zCenter.toRawBits(), metadata.xHalf.toRawBits())
            put(metadata.yHalf.toRawBits(), metadata.zHalf.toRawBits(), 0f.toRawBits(), 0f.toRawBits())
            put(metadata.skinningBoundsOffset, metadata.skinningBoundsCount, if (metadata.includeBaseBoundsInSkinning) 1 else 0, 0)
        }
    }

    private fun DoubleBufferedIntObject.writeRecord(metadata: SkinningBoundsRecord)
    {
        fill(SKINNING_BOUNDS_RECORD_INTS)
        {
            put(metadata.xMin.toRawBits(), metadata.yMin.toRawBits(), metadata.zMin.toRawBits(), metadata.xMax.toRawBits())
            put(metadata.yMax.toRawBits(), metadata.zMax.toRawBits(), metadata.boneIndex, 0)
        }
    }

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