package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.asset.types.Model.Companion.BASE_VERTEX_FLOATS
import no.njoh.pulseengine.core.asset.types.Model.Companion.MAX_BONE_INFLUENCES
import no.njoh.pulseengine.core.asset.types.Model.Companion.NORMAL_FLOAT_OFFSET
import no.njoh.pulseengine.core.asset.types.Model.Companion.TANGENT_FLOAT_OFFSET
import kotlin.math.floor
import kotlin.math.roundToInt

object ModelVertexCompressor
{
    fun compress(
        source: FloatArray,
        vertexCount: Int,
        sourceStride: Int,
        hasTexCoords: Boolean,
        hasBones: Boolean
    ): ByteArray {

        var offset = BASE_VERTEX_FLOATS
        val texCoordOffset = if (hasTexCoords) offset.also { offset += 2 } else -1
        val boneIndexOffset = if (hasBones) offset.also { offset += MAX_BONE_INFLUENCES } else -1
        val boneWeightOffset = if (hasBones) offset else -1

        val byteStride = getCompressedVertexStride(hasTexCoords, hasBones)
        val result = ByteArray(vertexCount * byteStride)
        val quantizedWeights = IntArray(MAX_BONE_INFLUENCES)
        val weightFractions = FloatArray(MAX_BONE_INFLUENCES)

        for (vertexIndex in 0 until vertexCount)
        {
            val src = vertexIndex * sourceStride
            var dst = vertexIndex * byteStride

            dst = result.putFloat(dst, source[src])
            dst = result.putFloat(dst, source[src + 1])
            dst = result.putFloat(dst, source[src + 2])

            dst = result.putPackedSnorm(
                index = dst,
                x = source[src + NORMAL_FLOAT_OFFSET],
                y = source[src + NORMAL_FLOAT_OFFSET + 1],
                z = source[src + NORMAL_FLOAT_OFFSET + 2],
                w = 0f
            )
            dst = result.putPackedSnorm(
                index = dst,
                x = source[src + TANGENT_FLOAT_OFFSET],
                y = source[src + TANGENT_FLOAT_OFFSET + 1],
                z = source[src + TANGENT_FLOAT_OFFSET + 2],
                w = source[src + TANGENT_FLOAT_OFFSET + 3]
            )

            if (texCoordOffset >= 0)
            {
                dst = result.putHalfFloat(dst, source[src + texCoordOffset])
                dst = result.putHalfFloat(dst, source[src + texCoordOffset + 1])
            }

            if (hasBones)
            {
                for (slot in 0 until MAX_BONE_INFLUENCES)
                {
                    dst = result.putUnsignedShort(
                        index = dst,
                        value = source[src + boneIndexOffset + slot].toInt().coerceIn(0, UShort.MAX_VALUE.toInt())
                    )
                }
                result.putUnorm8Weights(dst, source, src + boneWeightOffset, quantizedWeights, weightFractions)
            }
        }

        return result
    }

    fun getCompressedVertexStride(hasTexCoords: Boolean, hasBones: Boolean): Int
    {
        val bytes = 12 +                   // Position float32x3
            4 +                            // Normal snorm10x3
            4 +                            // Tangent snorm10x3 + 2-bit sign
            (if (hasTexCoords) 4 else 0) + // Texcoord float16x2
            (if (hasBones) 12 else 0)      // Bone indices uint16x4 + weights unorm8x4

        return align(bytes, 4)
    }

    private fun align(value: Int, alignment: Int): Int
    {
        val remainder = value % alignment
        return if (remainder == 0) value else value + alignment - remainder
    }

    private fun ByteArray.putFloat(index: Int, value: Float): Int =
        putInt(index, java.lang.Float.floatToRawIntBits(value))

    private fun ByteArray.putPackedSnorm(index: Int, x: Float, y: Float, z: Float, w: Float): Int
    {
        val packed = (quantizeSnorm(x, 10) and 0x3FF) or
            ((quantizeSnorm(y, 10) and 0x3FF) shl 10) or
            ((quantizeSnorm(z, 10) and 0x3FF) shl 20) or
            ((quantizeSnorm(w, 2) and 0x3) shl 30)
        return putInt(index, packed)
    }

    private fun quantizeSnorm(value: Float, bits: Int): Int
    {
        val maxValue = (1 shl (bits - 1)) - 1
        return (value.coerceIn(-1f, 1f) * maxValue).roundToInt().coerceIn(-maxValue, maxValue)
    }

    private fun ByteArray.putInt(index: Int, value: Int): Int
    {
        this[index] = (value and 0xFF).toByte()
        this[index + 1] = ((value ushr 8) and 0xFF).toByte()
        this[index + 2] = ((value ushr 16) and 0xFF).toByte()
        this[index + 3] = ((value ushr 24) and 0xFF).toByte()
        return index + 4
    }

    private fun ByteArray.putUnsignedShort(index: Int, value: Int): Int =
        putShort(index, value.coerceIn(0, UShort.MAX_VALUE.toInt()))

    private fun ByteArray.putShort(index: Int, value: Int): Int
    {
        this[index] = (value and 0xFF).toByte()
        this[index + 1] = ((value ushr 8) and 0xFF).toByte()
        return index + 2
    }

    private fun ByteArray.putHalfFloat(index: Int, value: Float): Int =
        putShort(index, floatToHalfBits(value))

    private fun ByteArray.putUnorm8Weights(index: Int, source: FloatArray, sourceOffset: Int, quantized: IntArray, fractions: FloatArray): Int 
    {
        var totalWeight = 0f
        for (slot in 0 until MAX_BONE_INFLUENCES)
            totalWeight += source[sourceOffset + slot].coerceAtLeast(0f)

        if (totalWeight <= 0f)
        {
            for (slot in 0 until MAX_BONE_INFLUENCES)
                this[index + slot] = 0
            return index + MAX_BONE_INFLUENCES
        }

        var quantizedSum = 0
        for (slot in 0 until MAX_BONE_INFLUENCES)
        {
            val scaled = source[sourceOffset + slot].coerceAtLeast(0f) / totalWeight * 255f
            val base = floor(scaled).toInt().coerceIn(0, 255)
            quantized[slot] = base
            fractions[slot] = scaled - base
            quantizedSum += base
        }

        repeat(255 - quantizedSum)
        {
            var bestSlot = 0
            for (slot in 1 until MAX_BONE_INFLUENCES)
                if (fractions[slot] > fractions[bestSlot])
                    bestSlot = slot

            quantized[bestSlot]++
            fractions[bestSlot] = -1f
        }

        for (slot in 0 until MAX_BONE_INFLUENCES)
            this[index + slot] = quantized[slot].coerceIn(0, 255).toByte()

        return index + MAX_BONE_INFLUENCES
    }

    private fun floatToHalfBits(value: Float): Int
    {
        val bits = java.lang.Float.floatToRawIntBits(value)
        val sign = (bits ushr 16) and 0x8000
        val exponent = (bits ushr 23) and 0xFF
        val mantissa = bits and 0x007FFFFF

        if (exponent == 0xFF)
            return sign or if (mantissa == 0) 0x7C00 else 0x7E00

        val halfExponent = exponent - 127 + 15
        if (halfExponent >= 0x1F)
            return sign or 0x7C00

        if (halfExponent <= 0)
        {
            if (halfExponent < -10)
                return sign

            val mantissaWithHiddenBit = mantissa or 0x00800000
            val shift = 14 - halfExponent
            var halfMantissa = mantissaWithHiddenBit ushr shift
            if (((mantissaWithHiddenBit ushr (shift - 1)) and 1) != 0)
                halfMantissa++
            return sign or halfMantissa
        }

        var half = sign or (halfExponent shl 10) or (mantissa ushr 13)
        if ((mantissa and 0x00001000) != 0)
            half++
        return half and 0xFFFF
    }
}