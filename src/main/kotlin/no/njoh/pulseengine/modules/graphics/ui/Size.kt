package no.njoh.pulseengine.modules.graphics.ui

import no.njoh.pulseengine.modules.graphics.ui.Size.ValueType.*

class Size internal constructor(
    value: Float,
    val type: ValueType,
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

    fun setOnUpdated(callback: () -> Unit)
    {
        onUpdatedCallback = callback
    }

    fun calculate(value: Float): Float = when (type)
    {
        ABSOLUTE -> this.value
        RELATIVE -> value * this.fraction
        AUTO -> value
    }

    companion object
    {
        fun absolute(value: Float) = Size(value, ABSOLUTE)
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

