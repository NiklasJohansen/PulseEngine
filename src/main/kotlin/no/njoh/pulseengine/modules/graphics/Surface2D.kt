package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.assets.Font
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.modules.graphics.postprocessing.PostProcessingPipeline
import no.njoh.pulseengine.modules.graphics.renderers.*
import org.lwjgl.opengl.GL11.*

interface Surface
{
    val width: Int
    val height: Int
    fun getTexture(): Texture
}

interface Surface2D : Surface
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
    fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float = 1f): Surface2D
    fun setDrawColor(color: Color): Surface2D
    fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float = 0f): Surface2D
    fun setBackgroundColor(color: Color): Surface2D
    fun setBlendFunction(func: BlendFunction): Surface2D
    fun setIsVisible(isVisible: Boolean): Surface2D
    fun addPostProcessingEffect(effect: PostProcessingEffect): Surface2D
    fun removePostProcessingEffect(effect: PostProcessingEffect): Surface2D
}

interface EngineSurface2D : Surface2D
{
    override val camera: CameraEngineInterface
    val name: String
    val zOrder: Int
    val isVisible: Boolean

    fun init(width: Int, height: Int, glContextRecreated: Boolean)
    fun render()
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

    override var width = 0
    override var height = 0
    override var isVisible = true

    private val backgroundColor = Color(0.1f, 0.1f, 0.1f, 0f)
    private var blendFunction = BlendFunction.NORMAL
    private val postProcessingPipeline = PostProcessingPipeline()
    private val renderers = listOf(
        uniColorLineRenderer,
        lineRenderer,
        textureRenderer,
        quadRenderer
    )

    override fun init(width: Int, height: Int, glContextRecreated: Boolean)
    {
        this.width = width
        this.height = height

        if (glContextRecreated)
        {
            renderers.forEach { it.init() }
            postProcessingPipeline.init()
        }

        renderTarget.init(width, height)
    }

    override fun render()
    {
        renderTarget.begin()
        setOpenGlState()
        camera.updateViewMatrix(width, height)
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

    override fun cleanup()
    {
        renderers.forEach { it.cleanup() }
        renderTarget.cleanUp()
        postProcessingPipeline.cleanUp()
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
        textRenderer.draw(this, text, x, y, font ?: Font.DEFAULT, fontSize, xOrigin, yOrigin)

    override fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float): Surface2D
    {
        renderState.setRGBA(red, green, blue, alpha)
        return this
    }

    override fun setDrawColor(color: Color): Surface2D
    {
        renderState.setRGBA(color.red, color.green, color.blue, color.alpha)
        return this
    }

    override fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float): Surface2D
    {
        backgroundColor.red = red
        backgroundColor.green = green
        backgroundColor.blue = blue
        backgroundColor.alpha = alpha
        return this
    }

    override fun setBackgroundColor(color: Color): Surface2D
    {
        backgroundColor.red = color.red
        backgroundColor.green = color.green
        backgroundColor.blue = color.blue
        backgroundColor.alpha = color.alpha
        return this
    }

    override fun setBlendFunction(func: BlendFunction): Surface2D
    {
        blendFunction = func
        return this
    }

    override fun setIsVisible(isVisible: Boolean): Surface2D
    {
        this.isVisible = isVisible
        return this
    }

    override fun addPostProcessingEffect(effect: PostProcessingEffect): Surface2D
    {
        postProcessingPipeline.addEffect(effect)
        return this
    }

    override fun removePostProcessingEffect(effect: PostProcessingEffect): Surface2D
    {
        postProcessingPipeline.removeEffect(effect)
        return this
    }

    override fun getTexture(): Texture =
        postProcessingPipeline.process(renderTarget.getTexture())

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


