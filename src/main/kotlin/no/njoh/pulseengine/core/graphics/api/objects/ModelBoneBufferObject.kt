package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.joml.Matrix4f

class ModelBoneBufferObject
{
    private var boneBuffer         = null as StreamingFloatBufferObject?
    private val bonePalettes       = ArrayList<Array<Matrix4f>>(128)
    private var bonePaletteOffsets = IntArray(128)
    private var boneMatrixCount    = 0
    private var dataSubmitted      = false
    private var dataDirty          = true

    fun clear()
    {
        boneBuffer?.clear()
        bonePalettes.clear()
        boneMatrixCount = 0
        dataSubmitted = false
        dataDirty = true
    }

    fun addBoneMatricesAndGetOffset(boneMatrices: Array<Matrix4f>?): Int
    {
        if (boneMatrices.isNullOrEmpty())
            return -1

        if (boneBuffer == null)
            boneBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(MODEL_BONE_BUFFER_BINDING, initCapacity = 16 * 512)

        for (i in 0 until bonePalettes.size)
        {
            if (boneMatrices === bonePalettes[i])
                return bonePaletteOffsets[i]
        }

        if (bonePalettes.size >= bonePaletteOffsets.size)
            bonePaletteOffsets = bonePaletteOffsets.copyOf(bonePaletteOffsets.size * 2)

        val offset = boneMatrixCount
        bonePalettes += boneMatrices
        bonePaletteOffsets[bonePalettes.lastIndex] = offset
        boneMatrixCount += boneMatrices.size

        boneBuffer!!.fill(boneMatrices.size * 16)
        {
            boneMatrices.forEachFast { putMatrix(it) }
        }

        dataDirty = true
        return offset
    }

    fun submit()
    {
        val boneBuffer = boneBuffer ?: return
        if (boneMatrixCount == 0)
            return

        if (dataSubmitted && dataDirty)
        {
            boneBuffer.markSubmittedDataInUse()
            dataSubmitted = false
        }

        if (dataDirty || !dataSubmitted)
        {
            boneBuffer.submit()
            dataSubmitted = true
            dataDirty = false
        }
        else boneBuffer.bindSubmittedRange()
    }

    fun markGpuDataInUse()
    {
        val boneBuffer = boneBuffer ?: return
        if (!dataSubmitted)
            return

        GpuProfiler.measure("sync model bone buffers")
        {
            boneBuffer.markSubmittedDataInUse()
        }
        dataSubmitted = false
    }

    fun destroy()
    {
        boneBuffer?.destroy()
        boneBuffer = null
        bonePalettes.clear()
        boneMatrixCount = 0
        dataSubmitted = false
        dataDirty = true
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
        const val MODEL_BONE_BUFFER_BINDING = 3
    }
}