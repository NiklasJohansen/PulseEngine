package no.njoh.pulseengine.modules.physics3d.box3d

internal fun Float.finiteOr(fallback: Float) = if (isFinite()) this else fallback

internal fun Float.nonNegativeOr(fallback: Float): Float
{
    val finiteValue = finiteOr(fallback)
    return if (finiteValue >= 0f) finiteValue else 0f
}