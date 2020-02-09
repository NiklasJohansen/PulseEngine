package engine.modules

import org.lwjgl.opengl.GL

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*

interface GraphicsInterface
{
    fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    fun drawImage(image: Image, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f)
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

        glEnable(GL_TEXTURE_2D)
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR.toFloat())
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR.toFloat())
    }

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    {
        glBindTexture(GL_TEXTURE_2D, 0)
        glColor4f(red, green, blue, alpha)
        glBegin(GL_QUADS)
        glVertex2f(x, y)
        glVertex2f(x + width, y)
        glVertex2f(x + width, y + height)
        glVertex2f(x, y + height)
        glEnd()
    }

    override fun drawImage(image: Image, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        glPushMatrix()
        glColor4f(red, green, blue, alpha)
        glBindTexture(GL_TEXTURE_2D, image.textureId)
        glTranslatef(x, y, 0f)
        glRotatef(rot, 0f, 0f, 1f)
        glTranslatef(-width*xOrigin, -height*yOrigin, 0f)
        glBegin(GL_QUADS)
            glTexCoord2f(0f, 0f)
            glVertex2f(0f, 0f)
            glTexCoord2f(0f, 1f)
            glVertex2f(0f, height)
            glTexCoord2f(1f,1f)
            glVertex2f(width, height)
            glTexCoord2f(1f, 0f)
            glVertex2f(width, 0f)
        glEnd()
        glPopMatrix()
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

    fun clearBuffer() = glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
}