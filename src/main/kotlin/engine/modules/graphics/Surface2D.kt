package engine.modules.graphics

import engine.data.Color
import engine.data.Font
import engine.data.Texture
import engine.modules.graphics.renderers.*
import org.lwjgl.opengl.GL11.*

interface Surface2D
{
    val camera: CameraInterface
    fun drawLinePoint(x: Float, y: Float)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    fun drawSameColorLines(block: (draw: LineRendererInterface) -> Unit)
    fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    fun drawQuadVertex(x: Float, y: Float)
    fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f)
    fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f)
    fun drawText(text: String, x: Float, y: Float, font: Font? = null, fontSize: Float = -1f, xOrigin: Float = 0f, yOrigin: Float = 0f)
    fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float = 1f)
    fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float = 0f)
    fun setBlendFunction(func: BlendFunction)
}

interface EngineSurface2D : Surface2D
{
    override val camera: CameraEngineInterface
    val name: String
    val zOrder: Int

    fun initRenderers()
    fun initRenderTargets(width: Int, height: Int)
    fun render()
    fun getTexture(): Texture
    fun cleanup()
}

class Surface2DImpl(
    override val name: String,
    override val zOrder: Int,
    override val camera: CameraEngineInterface,
    private val renderState: RenderState,
    private val graphicsState: GraphicsState,
    private val renderTarget: RenderTarget,
    private val textRenderer: TextRenderer,
    private val uniColorLineRenderer: UniColorLineBatchRenderer,
    private val quadRenderer: QuadBatchRenderer,
    private val lineRenderer: LineBatchRenderer,
    private val textureRenderer: TextureBatchRenderer
): EngineSurface2D {

    private val backgroundColor = Color(0.1f, 0.1f, 0.1f, 0f)
    private var blendFunction = BlendFunction.NORMAL
    private val renderers = listOf(
        uniColorLineRenderer,
        lineRenderer,
        textureRenderer,
        quadRenderer
    )

    override fun initRenderers() =
        renderers.forEach { it.init() }

    override fun initRenderTargets(width: Int, height: Int) =
        renderTarget.init(width, height)

    override fun render()
    {
        renderTarget.begin()
        setOpenGlState()
        camera.updateViewMatrix()
        renderers.forEach { it.render(camera) }
        renderTarget.end()
        renderState.resetDepth(camera.nearPlane)
    }

    private fun setOpenGlState()
    {
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDepthFunc(GL_LEQUAL)
        glDepthRange(camera.nearPlane.toDouble(), camera.farPlane.toDouble())

        glEnable(GL_BLEND)
        glBlendFunc(blendFunction.src, blendFunction.dest)

        glClearColor(backgroundColor.red, backgroundColor.green, backgroundColor.blue, backgroundColor.alpha)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glClearDepth(camera.farPlane.toDouble())
    }

    override fun getTexture(): Texture =
        renderTarget.getTexture()

    override fun cleanup()
    {
        renderers.forEach { it.cleanup() }
        renderTarget.cleanUp()
    }

    override fun drawSameColorLines(block: (draw: LineRendererInterface) -> Unit)
    {
        block(uniColorLineRenderer)
        uniColorLineRenderer.setColor(renderState.rgba)
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float) =
        lineRenderer.line(x0, y0, x1, y1)

    override fun drawLinePoint(x: Float, y: Float) =
        lineRenderer.linePoint(x, y)

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float) =
        quadRenderer.quad(x, y, width, height)

    override fun drawQuadVertex(x: Float, y: Float) =
        quadRenderer.vertex(x, y)

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float) =
        textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float) =
        textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin, uMin, vMin, uMax, vMax)

    override fun drawText(text: String, x: Float, y: Float, font: Font?, fontSize: Float, xOrigin: Float, yOrigin: Float) =
        textRenderer.draw(this, text, x, y, font ?: graphicsState.defaultFont, fontSize, xOrigin, yOrigin)

    override fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float) =
        renderState.setRGBA(red, green, blue, alpha)

    override fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float)
    {
        backgroundColor.red = red
        backgroundColor.green = green
        backgroundColor.blue = blue
        backgroundColor.alpha = alpha
    }

    override fun setBlendFunction(func: BlendFunction)
    {
        blendFunction = func
    }

    companion object
    {
        fun create(name: String, zOrder: Int, initCapacity: Int, graphicsState: GraphicsState, camera: CameraEngineInterface): Surface2DImpl
        {
            val renderState = RenderState()
            return Surface2DImpl(
                name = name,
                zOrder = zOrder,
                camera = camera,
                renderState = renderState,
                graphicsState = graphicsState,
                renderTarget = RenderTarget(),
                textRenderer = TextRenderer(),
                uniColorLineRenderer = UniColorLineBatchRenderer(initCapacity, renderState),
                quadRenderer = QuadBatchRenderer(initCapacity, renderState),
                lineRenderer = LineBatchRenderer(initCapacity, renderState),
                textureRenderer = TextureBatchRenderer(initCapacity, renderState, graphicsState)
            )
        }
    }
}