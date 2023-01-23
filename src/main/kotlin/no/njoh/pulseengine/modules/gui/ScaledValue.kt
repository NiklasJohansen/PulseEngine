@file:Suppress("NOTHING_TO_INLINE")

package no.njoh.pulseengine.modules.gui

import no.njoh.pulseengine.modules.gui.UiParams.UI_SCALE

@JvmInline
value class ScaledValue private constructor(val value: Float)
{
    companion object
    {
        fun of(v: Float) = ScaledValue(v * UI_SCALE)
        fun unscaled(v: Float) = ScaledValue(v)
    }

    inline operator fun plus(other: ScaledValue) = value + other.value
    inline operator fun minus(other: ScaledValue) = value - other.value
    inline operator fun times(other: ScaledValue) = value * other.value
    inline operator fun div(other: ScaledValue) = value / other.value

    inline operator fun plus(other: Float) = value + other
    inline operator fun minus(other: Float) = value - other
    inline operator fun times(other: Float) = value * other
    inline operator fun div(other: Float) = value / other
}

inline operator fun Float.plus(other: ScaledValue) = this + other.value
inline operator fun Float.minus(other: ScaledValue) = this - other.value
inline operator fun Float.times(other: ScaledValue) = this * other.value
inline operator fun Float.div(other: ScaledValue) = this / other.value