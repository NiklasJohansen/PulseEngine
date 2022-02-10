package no.njoh.pulseengine.modules.input

data class FocusArea(var x0: Float, var y0: Float, var x1: Float, var y1: Float)
{
    val height: Float
        get() = y1 - y0

    val width: Float
        get() = x1 - x0

    fun update(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        this.x0 = x0
        this.y0 = y0
        this.x1 = x1
        this.y1 = y1
    }

    fun isInside(x: Float, y: Float): Boolean =
        x >= x0 && x <= x1 && y >= y0 && y <= y1
}