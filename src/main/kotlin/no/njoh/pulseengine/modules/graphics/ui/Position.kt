package no.njoh.pulseengine.modules.graphics.ui

import no.njoh.pulseengine.modules.graphics.ui.Position.PositionType.*
import kotlin.math.max

class Position internal constructor(
    value: Float,
    val type: PositionType,
    val offset: Float = 0f
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

    fun calculate(value: Float): Float =
        calculate(value, value)

    fun calculate(minVal: Float, maxVal: Float): Float
    {
        val maxValue = max(minVal, maxVal)
        return when (type)
        {
            AUTO -> minVal
            FIXED -> this.value
            OFFSET -> minVal + offset
            CENTER -> (minVal + maxVal ) / 2f + offset
            MIN -> minVal + offset
            MAX -> maxValue + offset
        }.coerceIn(minVal, maxValue)
    }

    enum class PositionType
    {
        FIXED,
        OFFSET,
        MIN,
        MAX,
        CENTER,
        AUTO
    }

    companion object
    {
        fun auto() = Position(0f, AUTO)
        fun fixed(value: Float) = Position(value, FIXED)
        fun offset(offset: Float) = Position(0f, OFFSET, offset)
        fun center(offset: Float = 0f) = Position(0f, CENTER, offset)
        fun alignLeft(offset: Float = 0f) = Position(0f, MIN, offset)
        fun alignRight(offset: Float = 0f) = Position(0f, MAX, offset)
        fun alignTop(offset: Float = 0f) = Position(0f, MIN, offset)
        fun alignBottom(offset: Float = 0f) = Position(0f, MAX, offset)
    }
}

