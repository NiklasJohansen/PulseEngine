package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.*
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode
import org.joml.Matrix4f

internal class ModelBufferObject
{
    var instanceCount = 0; private set
    var instanceIndexMode = UNIFORM_OFFSET; private set
    var instanceIndexBuffer = null as StreamingIntBufferObject?; private set

    private lateinit var instanceBuffer: StreamingFloatBufferObject
    private lateinit var boneBuffer: StreamingFloatBufferObject

    private val bonePalettes       = ArrayList<Array<Matrix4f>>(128)
    private var bonePaletteOffsets = IntArray(128)
    private var boneMatrixCount    = 0

    fun init()
    {
        if (this::instanceBuffer.isInitialized)
            return

        instanceBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(
            blockBinding = INSTANCE_BUFFER_BINDING,
            initCapacity = 20 * 512
        )

        boneBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(
            blockBinding = BONE_BUFFER_BINDING,
            initCapacity = 16 * 512
        )

        instanceIndexMode = getSupportedModelInstanceIndexMode()
        if (instanceIndexMode == INSTANCE_ATTRIBUTE)
            instanceIndexBuffer = StreamingIntBufferObject.createArrayBuffer(initCapacity = 512)
    }

    fun clear()
    {
        instanceBuffer.clear()
        boneBuffer.clear()
        instanceIndexBuffer?.clear()
        bonePalettes.clear()
        boneMatrixCount = 0
        instanceCount = 0
    }

    fun addItem(item: RenderItem): Int
    {
        val instanceIndex = instanceCount++
        val materialId = item.material?.id ?: Material.DEFAULT_ID
        val boneOffset = addBones(item.boneMatrices)

        instanceBuffer.fill(20) // 16 + 4
        {
            putMatrix(item.transform)
            put(materialId.toFloat(), boneOffset.toFloat(), 0f, 0f)
        }

        instanceIndexBuffer?.fill(1)
        {
            put(instanceIndex)
        }

        return instanceIndex
    }

    fun submit()
    {
        instanceBuffer.submit()
        boneBuffer.submit()
        instanceIndexBuffer?.submit()
    }

    fun markSubmittedDataInUse()
    {
        instanceBuffer.markSubmittedDataInUse()
        boneBuffer.markSubmittedDataInUse()
        instanceIndexBuffer?.markSubmittedDataInUse()
    }

    fun destroy()
    {
        instanceBuffer.destroy()
        boneBuffer.destroy()
        instanceIndexBuffer?.destroy()
    }

    private fun addBones(boneMatrices: Array<Matrix4f>?): Int
    {
        if (boneMatrices.isNullOrEmpty())
            return -1

        for (i in 0 until bonePalettes.size)
        {
            if (boneMatrices === bonePalettes[i]) return bonePaletteOffsets[i]
        }

        if (bonePalettes.size >= bonePaletteOffsets.size)
            bonePaletteOffsets = bonePaletteOffsets.copyOf(bonePaletteOffsets.size * 2)

        val offset = boneMatrixCount
        bonePalettes += boneMatrices
        bonePaletteOffsets[bonePalettes.lastIndex] = offset
        boneMatrixCount += boneMatrices.size
        
        boneBuffer.fill(boneMatrices.size * 16)
        {
            for (matrix in boneMatrices) putMatrix(matrix)
        }

        return offset
    }

    private fun StreamingFloatBufferObject.putMatrix(matrix: Matrix4f)
    {
        put(matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03())
        put(matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13())
        put(matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23())
        put(matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33())
    }

    companion object
    {
        const val INSTANCE_BUFFER_BINDING = 1
        const val BONE_BUFFER_BINDING     = 3
    }
}
