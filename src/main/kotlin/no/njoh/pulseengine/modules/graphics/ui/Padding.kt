package no.njoh.pulseengine.modules.graphics.ui

class Padding(left: Float = 0f, right: Float = 0f, top: Float = 0f, bottom: Float = 0f)
{
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
        this.left = padding
        this.right = padding
        this.top = padding
        this.bottom = padding
        this.notify = true
        onUpdatedCallback.invoke()
    }

    fun setOnUpdated(callback: () -> Unit)
    {
        this.onUpdatedCallback = callback
    }
}