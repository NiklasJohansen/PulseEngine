package engine.modules.rendering

import engine.modules.Image
import org.lwjgl.opengl.GL11

// Exposed to game code
interface GraphicsInterface
{
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    fun drawLines(block: (draw: LineDrawCall) -> Unit)
    fun drawQuad(x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, depth: Float = 0f)
    fun drawQuads(block: (draw: QuadDrawCall) -> Unit)
    fun drawImage(image: Image, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, depth: Float = 0f)
    fun drawImages(image: Image, block: (draw: ImageDrawCall) -> Unit)
    fun setLineWidth(width: Float)
    fun setColor(red: Float, green: Float, blue: Float, alpha: Float = 1f)
    fun setBackgroundColor(red: Float, green: Float, blue: Float)
    fun setBlendFunction(func: BlendFunction)
}

// Exposed to game engine
interface GraphicsEngineInterface : GraphicsInterface
{
    fun init(viewPortWidth: Int, viewPortHeight: Int)
    fun cleanUp()
    fun updateViewportSize(width: Int, height: Int)
    fun clearBuffer()
    fun postRender()
}

// Defines functions allowed to call when drawing multiple quads
object QuadDrawCall {
    inline fun color(red: Float, green: Float, blue: Float, alpha: Float = 1f) = GL11.glColor4f(red, green, blue, alpha)
    inline fun quad(x: Float, y: Float, width: Float, height: Float)
    {
        GL11.glVertex2f(x, y)
        GL11.glVertex2f(x, y + height)
        GL11.glVertex2f(x + width, y + height)
        GL11.glVertex2f(x + width, y)
    }
}

// Defines functions allowed to call when drawing multiple lines
object LineDrawCall {
    inline fun color(red: Float, green: Float, blue: Float, alpha: Float = 1f) = GL11.glColor4f(red, green, blue, alpha)
    inline fun linePoint(x0: Float, y0: Float) = GL11.glVertex2f(x0, y0)
    inline fun line(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        GL11.glVertex2f(x0, y0)
        GL11.glVertex2f(x1, y1)
    }
}

// Defines functions allowed to call when drawing multiple images
object ImageDrawCall {
    inline fun color(red: Float, green: Float, blue: Float, alpha: Float = 1f) = GL11.glColor4f(red, green, blue, alpha)
    inline fun image(x: Float, y: Float, width: Float, height: Float)
    {
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(x, y)
        GL11.glTexCoord2f(0f, 1f)
        GL11.glVertex2f(x, y + height)
        GL11.glTexCoord2f(1f, 1f)
        GL11.glVertex2f(x + width, y + height)
        GL11.glTexCoord2f(1f, 0f)
        GL11.glVertex2f(x + width, y)
    }
}