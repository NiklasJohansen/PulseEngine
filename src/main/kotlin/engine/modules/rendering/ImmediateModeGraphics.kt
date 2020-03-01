package engine.modules.rendering

import engine.data.Font
import engine.data.Image
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

class ImmediateModeGraphics : GraphicsEngineInterface
{
    private val textRenderer = TextRenderer()

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        println("Initializing graphics...")
        updateViewportSize(viewPortWidth, viewPortHeight, true)
        setBackgroundColor(0.8f, 0.8f, 0.8f)
    }

    private fun initOpenGL()
    {
        GL.createCapabilities()
        glDepthFunc(GL_LEQUAL)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR.toFloat())
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR.toFloat())
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        glBindTexture(GL_TEXTURE_2D, 0)
        glBegin(GL_LINES)
            glVertex2f(x0, y0)
            glVertex2f(x1, y1)
        glEnd()
    }

    override fun drawLines(block: (draw: LineDrawCall) -> Unit)
    {
        glBindTexture(GL_TEXTURE_2D, 0)
        glBegin(GL_LINES)
        block(LineDrawCall)
        glEnd()
    }

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, depth: Float)
    {
        glPushMatrix()
        glBindTexture(GL_TEXTURE_2D, 0)
        glTranslatef(x, y, depth)
        glRotatef(rot, 0f, 0f, 1f)
        glTranslatef(-width * xOrigin, -height * yOrigin, 0f)
        glBegin(GL_QUADS)
            glVertex2f(0f, 0f)
            glVertex2f(0f, height)
            glVertex2f(width, height)
            glVertex2f(width, 0f)
        glEnd()
        glPopMatrix()
    }

    override inline fun drawQuads(block: (draw: QuadDrawCall) -> Unit)
    {
        glBindTexture(GL_TEXTURE_2D, 0)
        glBegin(GL_QUADS)
        block.invoke(QuadDrawCall)
        glEnd()
    }

    override fun drawImage(image: Image, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, depth: Float)
    {
        glPushMatrix()
        glBindTexture(GL_TEXTURE_2D, image.textureId)
        glTranslatef(x, y, depth)
        glRotatef(rot, 0f, 0f, 1f)
        glTranslatef(-width * xOrigin, -height * yOrigin, 0f)
        glBegin(GL_QUADS)
            glTexCoord2f(0f, 0f)
            glVertex2f(0f, 0f)
            glTexCoord2f(0f, 1f)
            glVertex2f(0f, height)
            glTexCoord2f(1f, 1f)
            glVertex2f(width, height)
            glTexCoord2f(1f, 0f)
            glVertex2f(width, 0f)
        glEnd()
        glPopMatrix()
    }

    override inline fun drawImages(image: Image, block: (draw: ImageDrawCall) -> Unit)
    {
        glBindTexture(GL_TEXTURE_2D, image.textureId)
        glBegin(GL_QUADS)
        block.invoke(ImageDrawCall)
        glEnd()
    }

    override fun drawText(text: String, x: Float, y: Float, font: Font, fontSize: Float, rotation: Float, xOrigin: Float, yOrigin: Float)
    {
        textRenderer.draw(text, x, y, font, fontSize, rotation, xOrigin, yOrigin)
    }

    override fun setColor(red: Float, green: Float, blue: Float, alpha: Float) = glColor4f(red, green, blue, alpha)

    override fun setLineWidth(width: Float) = glLineWidth(width)

    override fun setBackgroundColor(red: Float, green: Float, blue: Float) = glClearColor(red, green, blue, 1f)

    override fun setBlendFunction(func: BlendFunction) = glBlendFunc(func.src, func.dest)

    override fun clearBuffer() = glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

    override fun postRender()
    {
        // Not needed as all draw calls are made while rendering
    }

    override fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    {
        if(windowRecreated)
            initOpenGL()

        glViewport(0, 0, width, height)
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0.0, width.toDouble(), height.toDouble(), 0.0, 1.0, -1.0)
        glMatrixMode(GL_MODELVIEW)
    }

    override fun cleanUp()
    {
        println("Cleaning up graphics...")
    }
}

enum class BlendFunction(val src: Int, val dest: Int)
{
    NORMAL(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA),
    ADDITIVE(GL_SRC_ALPHA, GL_ONE),
    SCREEN(GL_ONE, GL_ONE_MINUS_SRC_COLOR)
}