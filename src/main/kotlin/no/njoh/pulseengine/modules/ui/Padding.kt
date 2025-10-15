package no.njoh.pulseengine.modules.ui

class Padding(
    left: ScaledValue = ScaledValue.of(0f),
    right: ScaledValue = ScaledValue.of(0f),
    top: ScaledValue = ScaledValue.of(0f),
    bottom: ScaledValue = ScaledValue.of(0f)
) {
    var left = left
        set(value) { field = value; if (notify) onUpdatedCallback() }

    var right = right
        set(value) { field = value; if (notify) onUpdatedCallback() }

    var top = top
        set(value) { field = value; if (notify) onUpdatedCallback() }

    var bottom = bottom
        set(value) { field = value; if (notify) onUpdatedCallback() }

    private var notify = true
    private var onUpdatedCallback: () -> Unit = {}

    fun setAll(padding: Float)
    {
        this.notify = false
        this.left = ScaledValue.of(padding)
        this.right = ScaledValue.of(padding)
        this.top = ScaledValue.of(padding)
        this.bottom = ScaledValue.of(padding)
        this.notify = true
        onUpdatedCallback.invoke()
    }

    fun setOnUpdated(callback: () -> Unit)
    {
        this.onUpdatedCallback = callback
    }
}