package no.njoh.pulseengine.core.shared.primitives

import kotlin.math.roundToInt

@JvmInline
value class CornerRadius(val data: Long)
{
    constructor(radius: Float) : this(radius, radius, radius, radius)

    constructor(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) : this(
        encode(topLeft) or (encode(topRight) shl 16) or (encode(bottomRight) shl 32) or (encode(bottomLeft) shl 48)
    )

    val topLeft      get() = decode(data)
    val topRight     get() = decode(data ushr 16)
    val bottomRight  get() = decode(data ushr 32)
    val bottomLeft   get() = decode(data ushr 48)

    val packedFloat0 get() = Float.fromBits(data.toInt())
    val packedFloat1 get() = Float.fromBits((data ushr 32).toInt())
    
    companion object
    {
        val ZERO = CornerRadius(0L)

        const val SCALE = 1 shl 4
        const val MAX_RADIUS = 0xFFFF.toFloat() / SCALE

        private fun encode(radius: Float): Long
        {
            val finiteRadius = when
            {
                radius.isNaN() -> 0f
                radius == Float.POSITIVE_INFINITY -> MAX_RADIUS
                else -> radius
            }
            return (finiteRadius.coerceIn(0f, MAX_RADIUS) * SCALE).roundToInt().coerceIn(0, 0xFFFF).toLong()
        }

        private fun decode(bits: Long) = (bits and 0xFFFFL).toFloat() / SCALE
    }
}
