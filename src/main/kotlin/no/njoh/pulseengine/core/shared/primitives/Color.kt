package no.njoh.pulseengine.core.shared.primitives

import no.njoh.pulseengine.core.shared.utils.Logger

data class Color(
    var red: Float = 1f,
    var green: Float = 1f,
    var blue: Float = 1f,
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

    fun setFrom(hex: String) = try
    {
        val c = java.awt.Color.decode(hex)
        this.red = c.red / 255f
        this.green = c.green / 255f
        this.blue = c.blue / 255f
        this.alpha = c.alpha / 255f
    }
    catch (e: Exception) { Logger.error("Failed to decode hex string: $hex to color. Reason ${e.message}") }

    companion object
    {
        val BLANK = Color(0f, 0f, 0f, 0f)
        val BLACK = Color(0f, 0f, 0f, 1f)
        val WHITE = Color(1f, 1f, 1f, 1f)
    }
}