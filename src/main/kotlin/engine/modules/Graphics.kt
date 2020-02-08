package engine.modules

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*

interface GraphicsInterface
{
    fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    fun setColor(red: Float, green: Float, blue: Float, alpha: Float)
}

class Graphics(viewPortWidth: Int, viewPortHeight: Int) : GraphicsInterface
{
    private var red: Float = 0.0f
    private var green: Float = 0.0f
    private var blue: Float = 0.0f
    private var alpha: Float = 0.0f

    init
    {
        GL.createCapabilities()
        glViewport(0,0, viewPortWidth, viewPortHeight)
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0.0, viewPortWidth.toDouble(), viewPortHeight.toDouble(), 0.0, 1.0, -1.0)
        glMatrixMode(GL_MODELVIEW)
        glClearColor(0.8f, 0.8f, 0.8f, 0.0f)
    }

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    {
        glColor3f(red, green, blue)
        glBegin(GL11.GL_QUADS)
        glVertex2f(x, y)
        glVertex2f(x + width, y)
        glVertex2f(x + width, y + height)
        glVertex2f(x, y + height)
        glEnd()
    }

    override fun setColor(red: Float, green: Float, blue: Float, alpha: Float)
    {
        this.red = red
        this.green = green
        this.blue = blue
        this.alpha = alpha
    }

    fun updateViewportSize(width: Int, height: Int)
    {
        glViewport(0,0, width, height)
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0.0, width.toDouble(), height.toDouble(), 0.0, 1.0, -1.0)
        glMatrixMode(GL_MODELVIEW)
    }

    fun clearBuffer() = glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
}