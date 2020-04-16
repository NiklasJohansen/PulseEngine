package engine.modules.graphics

import engine.data.Font
import engine.data.Texture
import engine.data.RenderMode
import org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

class ImmediateModeGraphics : GraphicsEngineInterface
{
    override val camera: CameraEngineInterface = Camera()
    private val textRenderer = TextRenderer()
    private val lineRenderer = ImmediateLineRenderer()

    private var blendFunc = BlendFunction.NORMAL
    private var bgRed = 0.1f
    private var bgGreen = 0.1f
    private var bgBlue = 0.1f

    private var red = 0.1f
    private var green = 0.1f
    private var blue = 0.1f
    private var alpha = 0.1f

    private var lineVertices = FloatArray(12)
    private var quadVertices = FloatArray(24)
    private var lineVertexCount = 0
    private var quadVertexCount = 0

    private lateinit var defaultFont: Font

    override fun getRenderMode() = RenderMode.IMMEDIATE

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        println("Initializing graphics...")

        updateViewportSize(viewPortWidth, viewPortHeight, true)

        // Load default font
        defaultFont = Font("/FiraSans-Regular.ttf", "default_font", floatArrayOf(24f))
        defaultFont.load()
        initTexture(defaultFont.charTexture)
    }

    override fun initTexture(texture: Texture)
    {
        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexImage2D(GL_TEXTURE_2D, 0, texture.format, texture.width, texture.height, 0, texture.format, GL_UNSIGNED_BYTE, texture.textureData)
        glGenerateMipmap(GL_TEXTURE_2D)
        texture.finalize(id)
    }

    private fun initOpenGL()
    {
        GL.createCapabilities()
        glEnable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR.toFloat())
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR.toFloat())
        setBackgroundColor(bgRed, bgGreen, bgBlue)
        setBlendFunction(blendFunc)
    }

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        glPushMatrix()
        glBindTexture(GL_TEXTURE_2D, 0)
        glMultMatrixf(camera.viewMatrixAsArray())
        glTranslatef(x, y, 0f)
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

    override fun drawQuadVertex(x: Float, y: Float)
    {
        quadVertices[quadVertexCount++] = red
        quadVertices[quadVertexCount++] = green
        quadVertices[quadVertexCount++] = blue
        quadVertices[quadVertexCount++] = alpha
        quadVertices[quadVertexCount++] = x
        quadVertices[quadVertexCount++] = y

        if (quadVertexCount == 24)
        {
            val v = quadVertices
            glPushMatrix()
            glMultMatrixf(camera.viewMatrixAsArray())
            glBindTexture(GL_TEXTURE_2D, 0)
            glBegin(GL_QUADS)
                glColor4f(v[0], v[1], v[2], v[3])
                glVertex2f(v[4], v[5])
                glColor4f(v[6], v[7], v[8], v[9])
                glVertex2f(v[10], v[11])
                glColor4f(v[12], v[13], v[14], v[15])
                glVertex2f(v[16], v[17])
                glColor4f(v[18], v[19], v[20], v[21])
                glVertex2f(v[22], v[23])
            glEnd()
            glPopMatrix()

           quadVertexCount = 0
        }
    }

    override fun drawImage(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        val uMin = texture.uMin
        val vMin = texture.vMin
        val uMax = texture.uMax
        val vMax = texture.vMax

        glPushMatrix()
        glBindTexture(GL_TEXTURE_2D, texture.textureId)
        glMultMatrixf(camera.viewMatrixAsArray())
        glTranslatef(x, y, 0f)
        glRotatef(rot, 0f, 0f, 1f)
        glTranslatef(-width * xOrigin, -height * yOrigin, 0f)
        glBegin(GL_QUADS)
            glTexCoord2f(uMin, vMin)
            glVertex2f(0f, 0f)
            glTexCoord2f(uMin, vMax)
            glVertex2f(0f, height)
            glTexCoord2f(uMax, vMax)
            glVertex2f(width, height)
            glTexCoord2f(uMax, vMin)
            glVertex2f(width, 0f)
        glEnd()
        glPopMatrix()
    }

    override fun drawImage(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        glPushMatrix()
        glBindTexture(GL_TEXTURE_2D, texture.textureId)
        glMultMatrixf(camera.viewMatrixAsArray())
        glTranslatef(x, y, 0f)
        glRotatef(rot, 0f, 0f, 1f)
        glTranslatef(-width * xOrigin, -height * yOrigin, 0f)
        glBegin(GL_QUADS)
            glTexCoord2f(uMin, vMin)
            glVertex2f(0f, 0f)
            glTexCoord2f(uMin, vMax)
            glVertex2f(0f, height)
            glTexCoord2f(uMax, vMax)
            glVertex2f(width, height)
            glTexCoord2f(uMax, vMin)
            glVertex2f(width, 0f)
        glEnd()
        glPopMatrix()
    }

    override fun drawText(text: String, x: Float, y: Float, font: Font?, fontSize: Float, xOrigin: Float, yOrigin: Float)
    {
        textRenderer.draw(this, text, x, y, font ?: defaultFont, fontSize, xOrigin, yOrigin)
    }

    override fun setColor(red: Float, green: Float, blue: Float, alpha: Float)
    {
        glColor4f(red, green, blue, alpha)
        this.red = red
        this.green = green
        this.blue = blue
        this.alpha = alpha
    }

    override fun setBackgroundColor(red: Float, green: Float, blue: Float)
    {
        glClearColor(red, green, blue, 1f)
        this.bgRed = red
        this.bgGreen = green
        this.bgBlue = blue
    }

    override fun setBlendFunction(func: BlendFunction)
    {
        glBlendFunc(func.src, func.dest)
        this.blendFunc = func
    }

    override fun setLineWidth(width: Float) = glLineWidth(width)

    override fun clearBuffer() = glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

    override fun postRender(interpolation: Float)
    {
        camera.updateViewMatrix(interpolation)
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        glPushMatrix()
        glBindTexture(GL_TEXTURE_2D, 0)
        glMultMatrixf(camera.viewMatrixAsArray())
        glBegin(GL_LINES)
            glVertex2f(x0, y0)
            glVertex2f(x1, y1)
        glEnd()
        glPopMatrix()
    }

    override fun drawLinePoint(x: Float, y: Float)
    {
        lineVertices[lineVertexCount++] = red
        lineVertices[lineVertexCount++] = green
        lineVertices[lineVertexCount++] = blue
        lineVertices[lineVertexCount++] = alpha
        lineVertices[lineVertexCount++] = x
        lineVertices[lineVertexCount++] = y

        if(lineVertexCount == 12)
        {
            glPushMatrix()
            glBindTexture(GL_TEXTURE_2D, 0)
            glMultMatrixf(camera.viewMatrixAsArray())
            glBegin(GL_LINES)
                glColor4f(lineVertices[0], lineVertices[1], lineVertices[2], lineVertices[3])
                glVertex2f(lineVertices[4], lineVertices[5])
                glColor4f(lineVertices[6], lineVertices[7], lineVertices[8], lineVertices[9])
                glVertex2f(lineVertices[10], lineVertices[11])
            glEnd()
            glPopMatrix()
            lineVertexCount = 0
        }
    }

    override fun drawSameColorLines(block: (draw: LineRendererInterface) -> Unit)
    {
        glPushMatrix()
        glBindTexture(GL_TEXTURE_2D, 0)
        glMultMatrixf(camera.viewMatrixAsArray())
        glBegin(GL_LINES)
        block(lineRenderer)
        glEnd()
        glPopMatrix()
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

    inner class ImmediateLineRenderer : LineRendererInterface
    {
        override fun linePoint(x0: Float, y0: Float) = glVertex2f(x0, y0)
        override fun line(x0: Float, y0: Float, x1: Float, y1: Float)
        {
            glVertex2f(x0, y0)
            glVertex2f(x1, y1)
        }
    }
}

enum class BlendFunction(val src: Int, val dest: Int)
{
    NORMAL(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA),
    ADDITIVE(GL_SRC_ALPHA, GL_ONE),
    SCREEN(GL_ONE, GL_ONE_MINUS_SRC_COLOR)
}


