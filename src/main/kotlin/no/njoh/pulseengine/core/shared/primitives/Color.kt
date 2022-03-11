package no.njoh.pulseengine.core.shared.primitives

data class Color(
    var red: Float,
    var green: Float,
    var blue: Float,
    var alpha: Float = 1f
) {
    constructor(red: Int, green: Int, blue: Int, alpha: Int = 255) :
        this(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    constructor(color: Color) :
        this(color.red, color.green, color.blue, color.alpha)

    fun setFrom(color: Color)
    {
        this.red = color.red
        this.green = color.green
        this.blue = color.blue
        this.alpha = color.alpha
    }

    fun setFrom(red: Float, green: Float, blue: Float, alpha: Float = 1f)
    {
        this.red = red
        this.green = green
        this.blue = blue
        this.alpha = alpha
    }

    companion object
    {
        val BLANK = Color(0f, 0f, 0f, 0f)
    }
}