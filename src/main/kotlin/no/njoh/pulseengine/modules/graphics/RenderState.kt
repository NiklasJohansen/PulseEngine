package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.modules.graphics.AntiAliasingType.NONE

class RenderState(
    var rgba: Float = 0f,
    var depth: Float = 0f,
    var hdrEnabled: Boolean = false,
    var antiAliasing: AntiAliasingType = NONE
) {
    init
    {
        setRGBA(1f, 1f, 1f, 1f)
    }

    fun increaseDepth()
    {
        depth += DEPTH_INC
    }

    fun resetDepth(value: Float)
    {
        depth = value
    }

    fun setRGBA(r: Float, g: Float, b: Float, a: Float)
    {
        val red   = (r * 255).toInt()
        val green = (g * 255).toInt()
        val blue  = (b * 255).toInt()
        val alpha = (a * 255).toInt()

        this.rgba = java.lang.Float.intBitsToFloat((red shl 24) or (green shl 16) or (blue shl 8) or alpha)
    }

    companion object
    {
        private const val DEPTH_INC = 0.000001f
    }
}