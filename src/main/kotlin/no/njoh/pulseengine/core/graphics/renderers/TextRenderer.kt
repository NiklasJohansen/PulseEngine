package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.graphics.Surface2D
import org.lwjgl.stb.STBTTAlignedQuad
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.*


class TextRenderer
{
    private val xb = FloatArray(1)
    private val yb = FloatArray(1)
    private val quad = STBTTAlignedQuad.malloc()
    private val advanceWidth = IntArray(1)
    private val leftSideBearing = IntArray(1)

    fun draw(surface: Surface2D, text: String, x: Float, y: Float, font: Font, fontSize: Float, xOrigin: Float, yOrigin: Float)
    {
        val fontIndex = font.fontSizes.indexOfLast { it <= fontSize }.takeIf { it != -1 } ?: 0
        val fontHeight = font.fontSizes[fontIndex]
        val textSize = if (fontSize > 0) fontSize else fontHeight
        val textScale = textSize / fontHeight

        val charData = font.charData
        val width = font.charTexture.width
        val height = font.charTexture.height
        val scale = stbtt_ScaleForPixelHeight(font.info, textSize)

        var xOffset = 0f
        if (xOrigin != 0f)
            xOffset = getWidth(text, font.info, scale) * xOrigin

        var yOffset = fontHeight * 0.5f
        if (yOrigin != 0f)
            yOffset -= 0.5f * textSize * (1f - yOrigin)

        xb[0] = x - xOffset
        yb[0] = y + yOffset
        charData.position(fontIndex * Font.TOTAL_CHAR_COUNT)

        var xChar = Float.MAX_VALUE
        var yChar = 0f
        var x0Last = 0f
        var y0Last = 0f

        for (character in text)
        {
            stbtt_GetPackedQuad(charData, width, height, character.toInt(), xb, yb, quad, false)
            val x0 = quad.x0()
            val y0 = quad.y0()
            val charWidth = (quad.x1() - x0) * textScale
            val charHeight = (quad.y1() - y0) * textScale

            if (xChar == Float.MAX_VALUE)
            {
                xChar = x0
                yChar = y0
            }
            else
            {
                xChar += (x0 - x0Last) * textScale
                yChar += (y0 - y0Last) * textScale
            }

            x0Last = x0
            y0Last = y0

            surface.drawTexture(font.charTexture, xChar, yChar, charWidth, charHeight, uMin = quad.s0(), vMin = quad.t0(), uMax = quad.s1(), vMax = quad.t1())
        }
    }

    private fun getWidth(text: String, fontInfo: STBTTFontinfo, scale: Float): Float
    {
        var length = 0f
        for (character in text)
        {
            stbtt_GetCodepointHMetrics(fontInfo, character.toInt(), advanceWidth, leftSideBearing)
            length += advanceWidth[0].toFloat()
        }
        return length * scale
    }
}