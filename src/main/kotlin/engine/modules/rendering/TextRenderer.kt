package engine.modules.rendering

import engine.data.Font
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glBegin
import org.lwjgl.opengl.GL11.glEnd
import org.lwjgl.opengl.GL11.glTexCoord2f
import org.lwjgl.opengl.GL11.glVertex2f
import org.lwjgl.opengl.GL30.*
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

    fun draw(text: String,  x: Float, y: Float, font: Font, fontSize: Float, rotation: Float, xOrigin: Float, yOrigin: Float)
    {
        val fontIndex = if(fontSize != -1f) font.fontSizes.indexOf(fontSize) else 0
        if(fontIndex == -1)
            throw IllegalArgumentException("Font size $fontSize not among available sizes [${font.fontSizes.joinToString()}] in font asset: ${font.name}")

        val charData = font.characterData
        val width = font.characterImage.width
        val height = font.characterImage.height
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

        GL11.glPushMatrix()
        glEnable(GL_TEXTURE_2D)
        glBindTexture(GL_TEXTURE_2D, font.characterImage.textureId)

        if (rotation != 0f)
        {
            glTranslatef(x, y, 0f)
            glRotatef(rotation, 0f, 0f, 1f)
            glTranslatef(-x, -y, 0f)
        }

        glBegin(GL_QUADS)
        for (character in text)
        {
            stbtt_GetPackedQuad(charData, width, height, character.toInt(), xb, yb, quad, true)
            drawQuad(
                quad.x0(), quad.y0(), quad.x1(), quad.y1(),
                quad.s0(), quad.t0(), quad.s1(), quad.t1()
            )
        }
        glEnd()
        GL11.glPopMatrix()
    }

    private fun drawQuad(x0: Float, y0: Float, x1: Float, y1: Float, s0: Float, t0: Float, s1: Float, t1: Float)
    {
        glTexCoord2f(s0, t0)
        glVertex2f(x0, y0)
        glTexCoord2f(s1, t0)
        glVertex2f(x1, y0)
        glTexCoord2f(s1, t1)
        glVertex2f(x1, y1)
        glTexCoord2f(s0, t1)
        glVertex2f(x0, y1)
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