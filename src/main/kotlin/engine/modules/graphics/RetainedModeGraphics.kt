package engine.modules.graphics

import engine.EngineInterface
import engine.data.Font
import engine.data.RenderMode
import engine.data.Texture
import engine.modules.graphics.renderers.LineRenderer
import engine.modules.graphics.renderers.QuadRenderer
import engine.modules.graphics.renderers.TextureRenderer
import engine.modules.graphics.renderers.UniColorLineRenderer
import org.joml.Matrix4f
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

data class RenderCollection(
    val textureRenderer: TextureRenderer,
    val quadRenderer: QuadRenderer,
    val lineRenderer: LineRenderer,
    val uniColorLineRenderer: UniColorLineRenderer
){
    private val renderers = listOf(
        uniColorLineRenderer,
        lineRenderer,
        textureRenderer,
        quadRenderer
    )
    fun init() = renderers.forEach { it.init() }
    fun cleanup() = renderers.forEach { it.cleanup() }
    fun render(camera: CameraEngineInterface) = renderers.forEach { it.render(camera) }
}

class RetainedModeGraphics : GraphicsEngineInterface
{
    override fun getRenderMode() = RenderMode.RETAINED
    override val camera: CameraEngineInterface = Camera()

    private val graphicState = GraphicsState()
    private val textRenderer = TextRenderer()
    private val worldRenderer = RenderCollection(
        TextureRenderer(100, graphicState),
        QuadRenderer(100, graphicState),
        LineRenderer(100, graphicState),
        UniColorLineRenderer(100, graphicState)
    )
    private val uiRenderer = RenderCollection(
        TextureRenderer(100, graphicState),
        QuadRenderer(100, graphicState),
        LineRenderer(100, graphicState),
        UniColorLineRenderer(100, graphicState)
    )

    private var currentRenderer: RenderCollection = worldRenderer
    private val renderers = listOf(worldRenderer, uiRenderer)

    private val farPlane = 5f
    private lateinit var defaultFont: Font

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        updateViewportSize(viewPortWidth, viewPortHeight, true)

        // Load default font
        defaultFont = Font("/FiraSans-Regular.ttf","default_font", floatArrayOf(24f, 72f))
        defaultFont.load()
        initTexture(defaultFont.charTexture)

        camera.setOnEnableChanged { enabled ->
            currentRenderer = if(enabled) worldRenderer else uiRenderer
        }
    }

    override fun initTexture(texture: Texture)
    {
        graphicState.textureArray.upload(texture)
    }

    override fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    {
        if(windowRecreated)
            initOpenGL()

        glViewport(0, 0, width, height)
        graphicState.projectionMatrix = Matrix4f().ortho(0.0f, width.toFloat(), height.toFloat(), 0.0f, -1f, farPlane)
        graphicState.modelMatrix = Matrix4f()
    }

    private fun initOpenGL()
    {
        GL.createCapabilities()
        glEnable(GL_BLEND)
        setBackgroundColor(graphicState.bgRed, graphicState.bgGreen, graphicState.bgBlue)
        setBlendFunction(graphicState.blendFunc)

        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDepthFunc(GL_LEQUAL)
        glDepthRange(-1.0, farPlane.toDouble())
        clearBuffer()

        renderers.forEach { it.init() }
    }

    override fun cleanUp()
    {
        renderers.forEach { it.cleanup() }
        graphicState.textureArray.cleanup()
        defaultFont.delete()
    }

    override fun clearBuffer()
    {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glClearDepth(farPlane.toDouble())
        graphicState.depth = -0.99f
    }

    override fun postRender(interplation: Float)
    {
        camera.enable()
        camera.updateViewMatrix(interplation)
        worldRenderer.render(camera)

        camera.disable()
        camera.updateViewMatrix(interplation)
        uiRenderer.render(camera)

        camera.enable()
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        currentRenderer.lineRenderer.line(x0, y0, x1, y1)
    }

    override fun drawLinePoint(x: Float, y: Float)
    {
        currentRenderer.lineRenderer.linePoint(x, y)
    }

    override fun drawSameColorLines(block: (draw: LineRendererInterface) -> Unit)
    {
        block(currentRenderer.uniColorLineRenderer)
        currentRenderer.uniColorLineRenderer.setColor(graphicState.rgba)
    }

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        currentRenderer.quadRenderer.quad(x, y, width, height)
    }

    override fun drawQuadVertex(x: Float, y: Float)
    {
        currentRenderer.quadRenderer.vertex(x, y)
    }

    override fun drawImage(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        currentRenderer.textureRenderer.drawImage(texture, x, y, width, height, rot, xOrigin, yOrigin)
    }

    override fun drawImage(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        currentRenderer.textureRenderer.drawImage(texture, x, y, width, height, rot, xOrigin, yOrigin, uMin, vMin, uMax, vMax)
    }

    override fun drawText(text: String, x: Float, y: Float, font: Font?, fontSize: Float, xOrigin: Float, yOrigin: Float)
    {
        textRenderer.draw(this, text, x, y, font ?: defaultFont, fontSize, xOrigin, yOrigin)
    }

    override fun setColor(red: Float, green: Float, blue: Float, alpha: Float)
    {
        graphicState.setRGBA(red, green, blue, alpha)
    }

    override fun setBackgroundColor(red: Float, green: Float, blue: Float)
    {
        glClearColor(red, green, blue, 1f)
        graphicState.bgRed = red
        graphicState.bgGreen = green
        graphicState.bgBlue = blue
    }

    override fun setBlendFunction(func: BlendFunction)
    {
        glBlendFunc(func.src, func.dest)
        graphicState.blendFunc = func
    }

    override fun setLineWidth(width: Float) { }
}

interface BatchRenderer
{
    fun init()
    fun cleanup()
    fun render(camera: CameraEngineInterface)
}
