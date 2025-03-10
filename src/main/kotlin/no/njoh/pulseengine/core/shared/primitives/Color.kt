package no.njoh.pulseengine.core.shared.primitives

import no.njoh.pulseengine.core.shared.utils.Logger
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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

    fun setFrom(color: Color): Color
    {
        this.red = color.red
        this.green = color.green
        this.blue = color.blue
        this.alpha = color.alpha
        return this
    }

    fun setFromRgba(red: Float, green: Float, blue: Float, alpha: Float = 1f): Color
    {
        this.red = red
        this.green = green
        this.blue = blue
        this.alpha = alpha
        return this
    }

    fun setFromHex(rgbHex: String): Color
    {
        try
        {
            val int    = Integer.decode(rgbHex)
            this.red   = ((int shr 16) and 0xFF) / 255f
            this.green = ((int shr 8) and 0xFF) / 255f
            this.blue  = (int and 0xFF) / 255f
            this.alpha = 1f
        }
        catch (e: Exception)
        {
            Logger.error { "Failed to decode hex string: $rgbHex to color. Reason ${e.message}" }
        }
        return this
    }

    fun setFromHsb(hsb: HSB) = setFromHsb(hsb.hue, hsb.saturation, hsb.brightness)

    fun setFromHsb(hue: Float, saturation: Float, brightness: Float): Color
    {
        if (saturation == 0f)
        {
            setFromRgba(brightness, brightness, brightness)
            return this
        }

        val h = (hue - floor(hue)) * 6.0f
        val f = h - floor(h)
        val p = brightness * (1.0f - saturation)
        val q = brightness * (1.0f - saturation * f)
        val t = brightness * (1.0f - saturation * (1.0f - f))
        when (h.toInt())
        {
            0 -> setFromRgba(brightness, t, p)
            1 -> setFromRgba(q, brightness, p)
            2 -> setFromRgba(p, brightness, t)
            3 -> setFromRgba(p, q, brightness)
            4 -> setFromRgba(t, p, brightness)
            5 -> setFromRgba(brightness, p, q)
        }
        return this
    }

    fun toHex(): String
    {
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun toHsb(): HSB
    {
        val r = (red * 255f).toInt()
        val g = (green * 255f).toInt()
        val b = (blue * 255f).toInt()
        val cMax = max(b, max(r, g))
        val cMin = min(b, min(r, g))
        val delta =  (cMax - cMin).toFloat()
        val hsb = HSB()
        hsb.brightness = cMax / 255.0f
        hsb.saturation = if (cMax != 0) delta / cMax else 0f
        if (hsb.saturation == 0f)
            return hsb

        val cRed   = (cMax - r) / delta
        val cGreen = (cMax - g) / delta
        val cBlue  = (cMax - b) / delta

        hsb.hue =
            if (r == cMax) cBlue - cGreen
            else if (g == cMax) 2f + cRed - cBlue
            else 4f + cGreen - cRed

        hsb.hue /= 6.0f
        if (hsb.hue < 0)
            hsb.hue += 1.0f

        return hsb
    }

    fun multiplyRgb(factor: Float): Color
    {
        red *= factor
        green *= factor
        blue *= factor
        return this
    }

    /**
     * Converts the color to linear space and returns a copy.
     * Does not modify the original color.
     */
    fun asLinear(): Color
    {
        REUSABLE_INSTANCE.red   = if (red   <= 0.04045f) red   / 12.92f else ((red   + 0.055f) / 1.055f).pow(2.4f)
        REUSABLE_INSTANCE.green = if (green <= 0.04045f) green / 12.92f else ((green + 0.055f) / 1.055f).pow(2.4f)
        REUSABLE_INSTANCE.blue  = if (blue  <= 0.04045f) blue  / 12.92f else ((blue  + 0.055f) / 1.055f).pow(2.4f)
        REUSABLE_INSTANCE.alpha = alpha
        return REUSABLE_INSTANCE
    }

    companion object
    {
        val BLANK = Color(0f, 0f, 0f, 0f)
        val BLACK = Color(0f, 0f, 0f, 1f)
        val WHITE = Color(1f, 1f, 1f, 1f)
        val RED   = Color(1f, 0f, 0f, 1f)
        val GREEN = Color(0f, 1f, 0f, 1f)
        val BLUE  = Color(0f, 0f, 1f, 1f)

        private val REUSABLE_INSTANCE = Color()
    }
}