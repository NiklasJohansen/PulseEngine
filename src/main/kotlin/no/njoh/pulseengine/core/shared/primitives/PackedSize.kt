package no.njoh.pulseengine.core.shared.primitives

@JvmInline
value class PackedSize(val data: Long)
{
    constructor(width: Int, height: Int) : this((width.toLong() and ((1L shl 32) - 1)) or (height.toLong() shl 32))
    constructor(width: Float, height: Float) : this((width.toLong() and ((1L shl 32) - 1)) or (height.toLong() shl 32))

    val width  get() = (data and ((1L shl 32) - 1)).toInt()
    val height get() = (data shr 32).toInt()

    operator fun component1() = width
    operator fun component2() = height
}