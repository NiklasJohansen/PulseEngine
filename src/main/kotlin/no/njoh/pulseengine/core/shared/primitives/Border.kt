package no.njoh.pulseengine.core.shared.primitives

import kotlin.math.roundToInt

@JvmInline
value class Border(val data: Long)
{
    constructor(color: Color, width: Float) : this(packColor(color).toLong() and 0xFFFF_FFFFL or (encodeWidth(width) shl 32))

    val rgba  get() = data.toInt()
    val width get() = decodeWidth(data ushr 32)

    val packedFloat0 get() = Float.fromBits(data.toInt())
    val packedFloat1 get() = Float.fromBits((data ushr 32).toInt())

    companion object
    {
        val ZERO = Border(0L)

        const val SCALE = 1 shl 4
        const val MAX_WIDTH = 0xFFFF.toFloat() / SCALE

        private fun packColor(color: Color): Int =
            (encodeColorChannel(color.red) shl 24) or
            (encodeColorChannel(color.green) shl 16) or
            (encodeColorChannel(color.blue) shl 8) or
            encodeColorChannel(color.alpha)

        private fun encodeColorChannel(value: Float): Int
        {
            val finiteValue = if (value.isFinite()) value else 0f
            return (finiteValue.coerceIn(0f, 1f) * 255f).roundToInt()
        }

        private fun encodeWidth(width: Float): Long
        {
            val finiteWidth = when
            {
                width.isNaN() -> 0f
                width == Float.POSITIVE_INFINITY -> MAX_WIDTH
                else -> width
            }
            return (finiteWidth.coerceIn(0f, MAX_WIDTH) * SCALE).roundToInt().coerceIn(0, 0xFFFF).toLong()
        }

        private fun decodeWidth(bits: Long) = (bits and 0xFFFFL).toFloat() / SCALE
    }
}
