package no.njoh.pulseengine.modules.ui

import no.njoh.pulseengine.modules.ui.UiParams.UI_SCALE
import no.njoh.pulseengine.modules.ui.Size.ValueType.*

class Size internal constructor(
    value: Float,
    var type: ValueType,
    var fraction: Float = 0f
) {
    var value = value
        set (value)
        {
            if (value != field)
            {
                field = value
                if (notify) onUpdatedCallback.invoke()
            }
        }

    private var notify = true
    private var onUpdatedCallback: () -> Unit = {}

    fun setQuiet(value: Float)
    {
        notify = false
        this.value = value
        notify = true
    }

    fun setQuiet(size: Size)
    {
        notify = false
        this.value = size.value
        this.fraction = size.fraction
        this.type = size.type
        notify = true
    }

    fun setOnUpdated(callback: () -> Unit)
    {
        onUpdatedCallback = callback
    }

    fun updateType(type: ValueType)
    {
        this.type = type
        onUpdatedCallback()
    }

    fun calculate(value: Float): Float = when (type)
    {
        ABSOLUTE -> this.value
        RELATIVE -> value * this.fraction
        AUTO -> value
    }

    operator fun plus(other: Size) = value / other.value
    operator fun minus(other: Size) = value - other.value
    operator fun times(other: Size) = value * other.value
    operator fun div(other: Size) = value / other.value

    operator fun plus(other: Float) = value / other
    operator fun minus(other: Float) = value - other
    operator fun times(other: Float) = value * other
    operator fun div(other: Float) = value / other

    companion object
    {
        fun absolute(value: Float) = Size(value * UI_SCALE, ABSOLUTE)
        fun absolute(value: ScaledValue) = Size(value.value, ABSOLUTE)
        fun relative(fraction: Float) = Size(0f, RELATIVE, fraction)
        fun auto() = Size(0f, AUTO)
    }

    enum class ValueType
    {
        ABSOLUTE,
        RELATIVE,
        AUTO
    }
}

