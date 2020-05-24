package engine.modules.graphics.renderers

import engine.data.Font
import engine.modules.graphics.GraphicsEngineInterface
import org.lwjgl.stb.STBTTAlignedQuad
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.*


class TextRenderer
{
    private val quad = STBTTAlignedQuad.malloc()
    private val xb = FloatArray(1)
    private val yb = FloatArray(1)
    private val advanceWidth = IntArray(1)
    private val leftSideBearing = IntArray(1)

    fun draw(gfx: GraphicsEngineInterface, text: String, x: Float, y: Float, font: Font, fontSize: Float, xOrigin: Float, yOrigin: Float)
    {
        val fontIndex = if(fontSize != -1f) font.fontSizes.indexOf(fontSize) else 0
        if(fontIndex == -1)
            throw IllegalArgumentException("Font size $fontSize not among available sizes [${font.fontSizes.joinToString()}] in font asset: ${font.name}")

        val charData = font.charData
        val width = font.charTexture.width
        val height = font.charTexture.height
        val fontHeight = font.fontSizes[fontIndex]
        val scale = stbtt_ScaleForPixelHeight(font.info, fontHeight)

        var xOffset = 0f
        if (xOrigin != 0f)
            xOffset = getWidth(text, font.info, scale) * xOrigin

        var yOffset = 0f
        if (yOrigin != 0f)
            yOffset = fontHeight * 0.5f * (1.0f - yOrigin)

        xb[0] = x - xOffset
        yb[0] = y + yOffset
        charData.position(fontIndex * 3 * 128)

        for (character in text)
        {
            stbtt_GetPackedQuad(charData, width, height, character.toInt(), xb, yb, quad, true)

            val xChar = quad.x0()
            val yChar = quad.y0()
            val charWidth = quad.x1() - xChar
            val charHeight = quad.y1() - yChar

            gfx.drawTexture(font.charTexture, xChar, yChar, charWidth, charHeight, uMin = quad.s0(), vMin = quad.t0(), uMax = quad.s1(), vMax = quad.t1())
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