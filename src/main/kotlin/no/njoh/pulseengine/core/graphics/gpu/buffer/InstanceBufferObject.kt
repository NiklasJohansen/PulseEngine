package no.njoh.pulseengine.core.graphics.gpu.buffer

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.*
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode
import no.njoh.pulseengine.core.shared.primitives.Mat4f
import no.njoh.pulseengine.core.shared.primitives.Mat4fProps
import no.njoh.pulseengine.core.shared.utils.Logger
import kotlin.math.abs

class InstanceBufferObject
{
    var instanceCount       = 0;                                 private set
    var instanceIndexMode   = UNIFORM_OFFSET;                    private set
    var instanceIndexBuffer = null as StreamingIntBufferObject?; private set

    private lateinit var instanceBuffer: StreamingFloatBufferObject

    private var loggedInvalidRenderId = false

    fun init()
    {
        if (this::instanceBuffer.isInitialized)
            return

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
        instanceBuffer.clear()
        instanceIndexBuffer?.clear()
        instanceCount = 0
    }

    fun reserveItemCapacity(itemCount: Int)
    {
        instanceBuffer.ensureWriteCapacity(itemCount * INSTANCE_FLOATS)
        instanceIndexBuffer?.ensureWriteCapacity(itemCount)
    }

    fun addItem(item: RenderItem, boneOffsetIndex: Int): Int
    {
        val materialId = item.material?.id ?: Material.DEFAULT_ID
        val transform = item.transform
        val transformData = transform.data
        val transformOffset = transform.offset

        val dstData   = instanceBuffer.data
        val dstOffset = instanceBuffer.size
        System.arraycopy(transformData, transformOffset, dstData, dstOffset, Mat4f.MATRIX_SIZE)

        val handedness = computeSurfaceNormalMatrix(
            inMatrixData = transformData,
            inMatrixOffset = transformOffset,
            outMatrixData = dstData,
            outMatrixOffset = dstOffset + NORMAL_MATRIX_FLOAT_OFFSET,
            matrixProperties = transform.properties
        )

        dstData[dstOffset + PARAMS_FLOAT_OFFSET    ] = materialId.toFloat()
        dstData[dstOffset + PARAMS_FLOAT_OFFSET + 1] = boneOffsetIndex.toFloat()
        dstData[dstOffset + PARAMS_FLOAT_OFFSET + 2] = handedness
        dstData[dstOffset + RENDER_ID_WORD_OFFSET  ] = Float.fromBits(encodeRenderId(item.renderId))
        instanceBuffer.size = dstOffset + INSTANCE_FLOATS

        val instanceIndex = instanceCount++
        instanceIndexBuffer?.put(instanceIndex)

        return instanceIndex
    }

    fun submit() = measure("Instance buffers")
    {
        instanceBuffer.submit()
        instanceIndexBuffer?.submit()
    }

    fun markSubmittedDataInUse() = measure("Instance buffers")
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

    private fun encodeRenderId(renderId: Long): Int
    {
        if (renderId == INVALID_RENDER_ID)
            return INVALID_RENDER_ID_BITS

        if (renderId < 0L || renderId > MAX_RENDER_ID)
        {
            if (!loggedInvalidRenderId)
            {
                Logger.error()
                {
                    "Render ID $renderId cannot be represented safely by the float-backed GPU render-ID field. " +
                    "Valid IDs are 0..$MAX_RENDER_ID; writing the invalid render ID instead."
                }
                loggedInvalidRenderId = true
            }
            return INVALID_RENDER_ID_BITS
        }

        return renderId.toInt()
    }

    /**
     * Calculates the matrix used to transform surface normals from model space to world space.
     * The returned value is the sign of the determinant and is used to correct tangent-space
     * handedness for mirrored transforms. Singular or non-finite transforms produce an identity
     * normal matrix and positive handedness so invalid values do not reach the shaders.
     */
    private fun computeSurfaceNormalMatrix(
        inMatrixData: FloatArray,
        inMatrixOffset: Int,
        outMatrixData: FloatArray,
        outMatrixOffset: Int,
        matrixProperties: Mat4fProps
    ): Float {

        if (matrixProperties.hasIdentityLinearTransform)
        {
            setIdentityNormalMatrix(outMatrixData, outMatrixOffset)
            return 1f
        }

        val m00 = inMatrixData[inMatrixOffset    ]; val m01 = inMatrixData[inMatrixOffset +  1]; val m02 = inMatrixData[inMatrixOffset +  2]
        val m10 = inMatrixData[inMatrixOffset + 4]; val m11 = inMatrixData[inMatrixOffset +  5]; val m12 = inMatrixData[inMatrixOffset +  6]
        val m20 = inMatrixData[inMatrixOffset + 8]; val m21 = inMatrixData[inMatrixOffset +  9]; val m22 = inMatrixData[inMatrixOffset + 10]

        if (matrixProperties.hasOrthonormalLinearTransform)
        {
            val determinant =
                m00 * (m11 * m22 - m12 * m21) +
                m10 * (m21 * m02 - m22 * m01) +
                m20 * (m01 * m12 - m02 * m11)

            if (determinant.isFinite() && abs(determinant) > MIN_NORMAL_DETERMINANT)
            {
                outMatrixData[outMatrixOffset     ] = m00; outMatrixData[outMatrixOffset +  1] = m01; outMatrixData[outMatrixOffset +  2] = m02; outMatrixData[outMatrixOffset +  3] = 0f
                outMatrixData[outMatrixOffset +  4] = m10; outMatrixData[outMatrixOffset +  5] = m11; outMatrixData[outMatrixOffset +  6] = m12; outMatrixData[outMatrixOffset +  7] = 0f
                outMatrixData[outMatrixOffset +  8] = m20; outMatrixData[outMatrixOffset +  9] = m21; outMatrixData[outMatrixOffset + 10] = m22; outMatrixData[outMatrixOffset + 11] = 0f
                return if (determinant < 0f) -1f else 1f
            }
        }

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
            setIdentityNormalMatrix(outMatrixData, outMatrixOffset)
            return 1f
        }

        val invDet = 1f / determinant
        outMatrixData[outMatrixOffset     ] = c00 * invDet; outMatrixData[outMatrixOffset +  1] = c01 * invDet; outMatrixData[outMatrixOffset +  2] = c02 * invDet; outMatrixData[outMatrixOffset +  3] = 0f
        outMatrixData[outMatrixOffset +  4] = c10 * invDet; outMatrixData[outMatrixOffset +  5] = c11 * invDet; outMatrixData[outMatrixOffset +  6] = c12 * invDet; outMatrixData[outMatrixOffset +  7] = 0f
        outMatrixData[outMatrixOffset +  8] = c20 * invDet; outMatrixData[outMatrixOffset +  9] = c21 * invDet; outMatrixData[outMatrixOffset + 10] = c22 * invDet; outMatrixData[outMatrixOffset + 11] = 0f
        return if (determinant < 0f) -1f else 1f
    }

    private fun setIdentityNormalMatrix(outData: FloatArray, outOffset: Int)
    {
        outData[outOffset     ] = 1f; outData[outOffset +  1] = 0f; outData[outOffset +  2] = 0f; outData[outOffset +  3] = 0f
        outData[outOffset +  4] = 0f; outData[outOffset +  5] = 1f; outData[outOffset +  6] = 0f; outData[outOffset +  7] = 0f
        outData[outOffset +  8] = 0f; outData[outOffset +  9] = 0f; outData[outOffset + 10] = 1f; outData[outOffset + 11] = 0f
    }

    companion object
    {
        // Every bit pattern through this value represents a finite float and is therefore preserved
        // exactly when the uint field is staged in the float-backed instance buffer.
        const val MAX_RENDER_ID = 0x7f7f_ffffL
        const val INVALID_RENDER_ID = -1L
        const val INVALID_RENDER_ID_BITS = 0x7f80_0000 // The bit pattern of a float representing NaN
        const val INSTANCE_FLOATS = 32
        const val NORMAL_MATRIX_FLOAT_OFFSET = 16
        const val PARAMS_FLOAT_OFFSET = 28
        const val RENDER_ID_WORD_OFFSET = PARAMS_FLOAT_OFFSET + 3

        const val INSTANCE_BUFFER_BINDING = 1
        const val INVALID_INSTANCE_INDEX = -1

        private const val MIN_NORMAL_DETERMINANT = 1e-8f

        fun decodeRenderId(bits: Int): Long
        {
            val renderId = bits.toLong() and 0xffff_ffffL
            return if (renderId <= MAX_RENDER_ID) renderId else INVALID_RENDER_ID
        }
    }
}