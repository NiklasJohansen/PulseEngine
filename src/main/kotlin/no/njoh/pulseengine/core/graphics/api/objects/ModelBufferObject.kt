package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.api.RenderItem
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.*
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode
import org.joml.Matrix4f

class ModelBufferObject
{
    var instanceCount = 0; private set
    var instanceIndexMode = UNIFORM_OFFSET; private set
    var instanceIndexBuffer = null as StreamingIntBufferObject?; private set

    private lateinit var instanceBuffer: StreamingFloatBufferObject
    private lateinit var boneBuffer: ModelBoneBufferObject

    fun init(boneBuffer: ModelBoneBufferObject)
    {
        this.boneBuffer = boneBuffer

        if (this::instanceBuffer.isInitialized)
            return

        instanceBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(
            blockBinding = INSTANCE_BUFFER_BINDING,
            initCapacity = 20 * 512
        )

        instanceIndexMode = getSupportedModelInstanceIndexMode()
        if (instanceIndexMode == INSTANCE_ATTRIBUTE)
            instanceIndexBuffer = StreamingIntBufferObject.createArrayBuffer(initCapacity = 512)
    }

    fun clear()
    {
        instanceBuffer.clear()
        instanceIndexBuffer?.clear()
        instanceCount = 0
    }

    fun addItem(item: RenderItem): Int
    {
        val instanceIndex = instanceCount++
        val materialId = item.material?.id ?: Material.DEFAULT_ID
        val boneOffset = boneBuffer.addBoneMatricesAndGetOffset(item.boneMatrices)

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

    fun submit() = measure("submit model buffers")
    {
        instanceBuffer.submit()
        instanceIndexBuffer?.submit()
        boneBuffer.submit()
    }

    fun bindSubmittedRange()
    {
        instanceBuffer.bindSubmittedRange()
        instanceIndexBuffer?.bindSubmittedRange()
        boneBuffer.submit()
    }

    fun markSubmittedDataInUse() = measure("sync model buffers")
    {
        instanceBuffer.markSubmittedDataInUse()
        instanceIndexBuffer?.markSubmittedDataInUse()
    }

    fun destroy()
    {
        if (!this::instanceBuffer.isInitialized)
            return
        instanceBuffer.destroy()
        instanceIndexBuffer?.destroy()
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
        const val INVALID_INSTANCE_INDEX = -1

        fun isValidInstanceIndex(index: Int) = index != INVALID_INSTANCE_INDEX
    }
}