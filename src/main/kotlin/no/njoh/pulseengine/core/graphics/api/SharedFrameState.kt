package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.graphics.api.objects.StreamingFloatBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.joml.Matrix4f

class SharedFrameState
{
    private var modelBoneBuffer = null as StreamingFloatBufferObject?

    private val modelBonePalettes       = ArrayList<Array<Matrix4f>>(128)
    private var modelBonePaletteOffsets = IntArray(128)
    private var modelBoneMatrixCount    = 0
    private var modelBoneDataSubmitted  = false
    private var modelBoneDataDirty      = true

    fun beginFrame()
    {
        modelBoneBuffer?.clear()
        modelBonePalettes.clear()
        modelBoneMatrixCount = 0
        modelBoneDataSubmitted = false
        modelBoneDataDirty = true
    }

    fun addBoneMatricesAndGetBoneOffset(boneMatrices: Array<Matrix4f>?): Int
    {
        if (boneMatrices.isNullOrEmpty())
            return -1

        if (modelBoneBuffer == null)
            modelBoneBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(MODEL_BONE_BUFFER_BINDING, initCapacity = 16 * 512)
        
        for (i in 0 until modelBonePalettes.size)
        {
            if (boneMatrices === modelBonePalettes[i]) 
                return modelBonePaletteOffsets[i]
        }

        if (modelBonePalettes.size >= modelBonePaletteOffsets.size)
            modelBonePaletteOffsets = modelBonePaletteOffsets.copyOf(modelBonePaletteOffsets.size * 2)

        val offset = modelBoneMatrixCount
        modelBonePalettes += boneMatrices
        modelBonePaletteOffsets[modelBonePalettes.lastIndex] = offset
        modelBoneMatrixCount += boneMatrices.size

        modelBoneBuffer!!.fill(boneMatrices.size * 16)
        {
            boneMatrices.forEachFast { putMatrix(it) }
        }

        modelBoneDataDirty = true
        return offset
    }

    fun submitModelData()
    {
        val modelBoneBuffer = modelBoneBuffer ?: return
        if (modelBoneMatrixCount == 0)
            return

        if (modelBoneDataSubmitted && modelBoneDataDirty)
        {
            modelBoneBuffer.markSubmittedDataInUse()
            modelBoneDataSubmitted = false
        }

        if (modelBoneDataDirty || !modelBoneDataSubmitted)
        {
            modelBoneBuffer.submit()
            modelBoneDataSubmitted = true
            modelBoneDataDirty = false
        } 
        else modelBoneBuffer.bindSubmittedRange()
    }

    fun endFrame()
    {
        val modelBoneBuffer = modelBoneBuffer ?: return

        if (modelBoneDataSubmitted)
        {
            GpuProfiler.measure("sync shared frame state")
            {
                modelBoneBuffer.markSubmittedDataInUse()
            }
            modelBoneDataSubmitted = false
        }
    }

    fun destroy()
    {
        modelBoneBuffer?.destroy()
        modelBoneBuffer = null
        modelBonePalettes.clear()
        modelBoneMatrixCount = 0
        modelBoneDataSubmitted = false
        modelBoneDataDirty = true
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