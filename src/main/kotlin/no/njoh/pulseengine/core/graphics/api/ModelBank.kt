package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.SubMesh
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
    private val subMeshMetadata = ArrayList<SubMeshMetadata>(128)
    private var metadataDirty = false

    fun upload(model: Model)
    {
        if ((model.vbo == null && model.vertexBytes.isEmpty()) || (model.ebo == null && model.indices.isEmpty()))
        {
            Logger.warn { "Attempted to upload empty mesh to GPU: ${model.name} (${model.filePath})" }
            return
        }

        uploadMesh(model)
        uploadMetadata(model)
    }

    fun submitAndBind()
    {
        val buffer = metadataBuffer ?: return

        if (metadataDirty)
        {
            for (record in subMeshMetadata)
                buffer.writeRecord(record)

            buffer.swapBuffers()
            buffer.bind()
            buffer.submit()
            buffer.release()

            metadataDirty = false
        }
        else
        {
            buffer.bind()
            buffer.release()
        }
    }

    fun destroy()
    {
        metadataBuffer?.destroy()
        metadataBuffer = null
        subMeshMetadata.clear()
        metadataDirty = false
    }

    private fun uploadMesh(model: Model)
    {
        val vao = VertexArrayObject.createAndBind()

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
    }

    private fun uploadMetadata(model: Model)
    {
        ensureMetadataBuffer()

        for (subMesh in model.subMeshes)
        {
            val record = SubMeshMetadata.from(subMesh)
            val index = subMesh.gpuMetaIndex

            if (index in subMeshMetadata.indices)
            {
                subMeshMetadata[index] = record
            }
            else
            {
                subMesh.gpuMetaIndex = subMeshMetadata.size
                subMeshMetadata += record
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

    private fun DoubleBufferedIntObject.writeRecord(record: SubMeshMetadata)
    {
        fill(RECORD_INTS)
        {
            put(record.indexCount, record.indexStart, record.baseVertex, 0)
            put(record.xCenter.toRawBits(), record.yCenter.toRawBits(), record.zCenter.toRawBits(), record.xHalf.toRawBits())
            put(record.yHalf.toRawBits(), record.zHalf.toRawBits(), 0f.toRawBits(), 0f.toRawBits())
        }
    }

    private data class SubMeshMetadata(
        val indexCount: Int,
        val indexStart: Int,
        val baseVertex: Int,
        val xCenter: Float,
        val yCenter: Float,
        val zCenter: Float,
        val xHalf: Float,
        val yHalf: Float,
        val zHalf: Float
    ) {
        companion object
        {
            fun from(subMesh: SubMesh): SubMeshMetadata
            {
                val bounds = subMesh.animatedBounds ?: subMesh.localBounds
                return SubMeshMetadata(
                    indexCount = subMesh.indexCount,
                    indexStart = subMesh.indexStart,
                    baseVertex = 0,
                    xCenter = (bounds.xMin + bounds.xMax) * 0.5f,
                    yCenter = (bounds.yMin + bounds.yMax) * 0.5f,
                    zCenter = (bounds.zMin + bounds.zMax) * 0.5f,
                    xHalf   = (bounds.xMax - bounds.xMin) * 0.5f,
                    yHalf   = (bounds.yMax - bounds.yMin) * 0.5f,
                    zHalf   = (bounds.zMax - bounds.zMin) * 0.5f
                )
            }
        }
    }

    companion object
    {
        const val METADATA_BUFFER_BINDING = 7
        private const val RECORD_INTS = 12
    }
}