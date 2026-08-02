package no.njoh.pulseengine.core.graphics.gpu.buffer

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.*
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode
import no.njoh.pulseengine.core.shared.primitives.Mat4f
import kotlin.math.abs

class InstanceBufferObject
{
    var instanceCount = 0; private set
    var instanceIndexMode = UNIFORM_OFFSET; private set
    var instanceIndexBuffer = null as StreamingIntBufferObject?; private set

    private lateinit var objectIdBuffer: StreamingIntBufferObject
    private lateinit var instanceBuffer: StreamingFloatBufferObject

    fun init()
    {
        if (this::instanceBuffer.isInitialized)
            return
        objectIdBuffer = StreamingIntBufferObject.createUnboundShaderStorageBuffer(initCapacity = 2 * 512)
        instanceBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(
            blockBinding = INSTANCE_BUFFER_BINDING,
            initCapacity = INSTANCE_FLOATS * 512
        )

        instanceIndexMode = getSupportedModelInstanceIndexMode()
        if (instanceIndexMode == INSTANCE_ATTRIBUTE)
            instanceIndexBuffer = StreamingIntBufferObject.createArrayBuffer(initCapacity = 512)
    }

    fun clear()
    {
        objectIdBuffer.clear()
        instanceBuffer.clear()
        instanceIndexBuffer?.clear()
        instanceCount = 0
    }

    fun addItem(item: RenderItem, boneOffsetIndex: Int): Int
    {
        val instanceIndex = instanceCount++
        val materialId = item.material?.id ?: Material.DEFAULT_ID
        val transform = item.transform
        val transformData = transform.data
        val transformOffset = transform.offset

        instanceBuffer.fill(INSTANCE_FLOATS)
        {
            val dstOffset = instanceBuffer.size
            val dstData   = instanceBuffer.data
            System.arraycopy(transformData, transformOffset, dstData, dstOffset, Mat4f.MATRIX_SIZE)

            val handedness = computeNormalMatrix(
                matrixData = transformData,
                matrixOffset = transformOffset,
                outData = dstData,
                outOffset = dstOffset + NORMAL_MATRIX_FLOAT_OFFSET
            )

            dstData[dstOffset + PARAMS_FLOAT_OFFSET    ] = materialId.toFloat()
            dstData[dstOffset + PARAMS_FLOAT_OFFSET + 1] = boneOffsetIndex.toFloat()
            dstData[dstOffset + PARAMS_FLOAT_OFFSET + 2] = handedness
            dstData[dstOffset + PARAMS_FLOAT_OFFSET + 3] = 0f
            size = dstOffset + INSTANCE_FLOATS
        }

        objectIdBuffer.fill(2)
        {
            put(encodeObjectIdLow(item.objectId), encodeObjectIdHigh(item.objectId))
        }

        instanceIndexBuffer?.fill(1)
        {
            put(instanceIndex)
        }

        return instanceIndex
    }

    fun submit() = measure("Instance buffers")
    {
        objectIdBuffer.submit()
        instanceBuffer.submit()
        instanceIndexBuffer?.submit()
    }

    fun bindObjectIds() = objectIdBuffer.bindSubmittedRange(OBJECT_ID_BUFFER_BINDING)

    fun markSubmittedDataInUse() = measure("Instance buffers")
    {
        instanceBuffer.markSubmittedDataInUse()
        objectIdBuffer.markSubmittedDataInUse()
        instanceIndexBuffer?.markSubmittedDataInUse()
    }

    fun destroy()
    {
        if (!this::instanceBuffer.isInitialized)
            return

        instanceBuffer.destroy()
        objectIdBuffer.destroy()
        instanceIndexBuffer?.destroy()
    }

    companion object
    {
        const val INSTANCE_BUFFER_BINDING = 1
        const val OBJECT_ID_BUFFER_BINDING = 12
        const val INVALID_INSTANCE_INDEX = -1
        const val INSTANCE_FLOATS = 32
        const val INSTANCE_BYTES = INSTANCE_FLOATS * Float.SIZE_BYTES
        const val NORMAL_MATRIX_FLOAT_OFFSET = 16
        const val PARAMS_FLOAT_OFFSET = 28

        private const val MIN_NORMAL_DETERMINANT = 1e-8f

        fun encodeObjectIdLow(id: Long) = id.toInt()
        fun encodeObjectIdHigh(id: Long) = (id ushr 32).toInt()
        fun decodeObjectId(low: Int, high: Int) = (low.toLong() and 0xffffffffL) or (high.toLong() shl 32)

        /**
         * Calculates the matrix used to transform surface normals from model space to world space.
         * The returned value is the sign of the determinant and is used to correct tangent-space
         * handedness for mirrored transforms. Singular or non-finite transforms produce an identity
         * normal matrix and positive handedness so invalid values do not reach the shaders.
         */
        private fun computeNormalMatrix(
            matrixData: FloatArray,
            matrixOffset: Int,
            outData: FloatArray,
            outOffset: Int
        ): Float {
            val m00 = matrixData[matrixOffset    ]; val m01 = matrixData[matrixOffset +  1]; val m02 = matrixData[matrixOffset +  2]
            val m10 = matrixData[matrixOffset + 4]; val m11 = matrixData[matrixOffset +  5]; val m12 = matrixData[matrixOffset +  6]
            val m20 = matrixData[matrixOffset + 8]; val m21 = matrixData[matrixOffset +  9]; val m22 = matrixData[matrixOffset + 10]

            val c00 = m11 * m22 - m12 * m21
            val c01 = m12 * m20 - m10 * m22
            val c02 = m10 * m21 - m11 * m20
            val c10 = m21 * m02 - m22 * m01
            val c11 = m22 * m00 - m20 * m02
            val c12 = m20 * m01 - m21 * m00
            val c20 = m01 * m12 - m02 * m11
            val c21 = m02 * m10 - m00 * m12
            val c22 = m00 * m11 - m01 * m10
            val determinant = m00 * c00 + m10 * c10 + m20 * c20

            if (!determinant.isFinite() || abs(determinant) <= MIN_NORMAL_DETERMINANT ||
                !c00.isFinite() || !c01.isFinite() || !c02.isFinite() ||
                !c10.isFinite() || !c11.isFinite() || !c12.isFinite() ||
                !c20.isFinite() || !c21.isFinite() || !c22.isFinite()
            ) {
                outData[outOffset     ] = 1f; outData[outOffset +  1] = 0f; outData[outOffset +  2] = 0f; outData[outOffset +  3] = 0f
                outData[outOffset +  4] = 0f; outData[outOffset +  5] = 1f; outData[outOffset +  6] = 0f; outData[outOffset +  7] = 0f
                outData[outOffset +  8] = 0f; outData[outOffset +  9] = 0f; outData[outOffset + 10] = 1f; outData[outOffset + 11] = 0f
                return 1f
            }

            val invDet = 1f / determinant
            outData[outOffset     ] = c00 * invDet; outData[outOffset +  1] = c01 * invDet; outData[outOffset +  2] = c02 * invDet; outData[outOffset +  3] = 0f
            outData[outOffset +  4] = c10 * invDet; outData[outOffset +  5] = c11 * invDet; outData[outOffset +  6] = c12 * invDet; outData[outOffset +  7] = 0f
            outData[outOffset +  8] = c20 * invDet; outData[outOffset +  9] = c21 * invDet; outData[outOffset + 10] = c22 * invDet; outData[outOffset + 11] = 0f
            return if (determinant < 0f) -1f else 1f
        }
    }
}