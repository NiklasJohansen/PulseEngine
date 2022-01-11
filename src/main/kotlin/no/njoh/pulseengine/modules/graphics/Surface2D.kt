package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.assets.Font
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.AntiAliasingType.NONE
import no.njoh.pulseengine.modules.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.modules.graphics.postprocessing.PostProcessingPipeline
import no.njoh.pulseengine.modules.graphics.renderers.*
import no.njoh.pulseengine.util.firstOrNullFast
import no.njoh.pulseengine.util.forEachFast
import org.lwjgl.opengl.GL11.*
import kotlin.reflect.KClass

interface Surface
{
    val name: String
    val width: Int
    val height: Int
    val zOrder: Int
    val textureScale: Float
    fun getTexture(): Texture
}

interface Surface2D : Surface
{
    val camera: Camera

    // Drawing
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    fun drawLineVertex(x: Float, y: Float)
    fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    fun drawQuadVertex(x: Float, y: Float)
    fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f)
    fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f)
    fun drawText(text: String, x: Float, y: Float, font: Font? = null, fontSize: Float = -1f, xOrigin: Float = 0f, yOrigin: Float = 0f)

    // Property setters
    fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float = 1f): Surface2D
    fun setDrawColor(color: Color): Surface2D
    fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float = 0f): Surface2D
    fun setBackgroundColor(color: Color): Surface2D
    fun setBlendFunction(func: BlendFunction): Surface2D
    fun setAntiAliasingType(antiAliasing: AntiAliasingType): Surface2D
    fun setHdrEnabled(enabled: Boolean): Surface2D
    fun setIsVisible(isVisible: Boolean): Surface2D
    fun setTextureScale(scale: Float): Surface2D

    // Post processing
    fun addPostProcessingEffect(effect: PostProcessingEffect): Surface2D
    fun removePostProcessingEffect(effect: PostProcessingEffect): Surface2D
    fun reloadPostProcessingShaders()

    // Renderers
    fun addAndInitializeRenderer(renderer: BatchRenderer): Surface2D
    fun <T: BatchRenderer> getRenderer(type: KClass<T>): T?
}

interface Surface2DInternal : Surface2D
{
    override val camera: CameraInternal
    val isVisible: Boolean
    fun init(width: Int, height: Int, glContextRecreated: Boolean)
    fun render()
    fun cleanup()
}

class Surface2DImpl(
    override val name: String,
    override val zOrder: Int,
    override val camera: CameraInternal,
    private val renderState: RenderState,
    private val textRenderer: TextRenderer,
    private val quadRenderer: QuadBatchRenderer,
    private val lineRenderer: LineBatchRenderer,
    private val textureRenderer: TextureBatchRenderer
): Surface2DInternal {

    override var width = 0
    override var height = 0
    override var isVisible = true
    override var textureScale = 1f

    private var renderTarget = createRenderTarget(textureScale, renderState)
    private val backgroundColor = Color(0.1f, 0.1f, 0.1f, 0f)
    private var blendFunction = BlendFunction.NORMAL
    private val postProcessingPipeline = PostProcessingPipeline()
    private val renderers = mutableListOf(
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
            renderers.forEachFast { it.init() }
            postProcessingPipeline.init()
        }

        renderTarget.init(width, height)
    }

    override fun render()
    {
        renderTarget.begin()
        setOpenGlState()
        renderers.forEachFast { it.render(this) }
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

        glViewport(0, 0, (width * textureScale).toInt(), (height * textureScale).toInt())
    }

    override fun cleanup()
    {
        renderers.forEachFast { it.cleanUp() }
        renderTarget.cleanUp()
        postProcessingPipeline.cleanUp()
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float) =
        lineRenderer.line(x0, y0, x1, y1)

    override fun drawLineVertex(x: Float, y: Float) =
        lineRenderer.lineVertex(x, y)

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

    override fun setAntiAliasingType(antiAliasing: AntiAliasingType): Surface2D
    {
        if (antiAliasing != renderState.antiAliasing)
        {
            renderState.antiAliasing = antiAliasing
            renderTarget.cleanUp()
            renderTarget = createRenderTarget(textureScale, renderState)
            renderTarget.init(width, height)
        }
        return this
    }

    override fun setHdrEnabled(enabled: Boolean): Surface2D
    {
        if (enabled != renderState.hdrEnabled)
        {
            renderState.hdrEnabled = enabled
            renderTarget.hdrEnabled = enabled
            renderTarget.init(width, height)
        }
        return this
    }

    override fun setIsVisible(isVisible: Boolean): Surface2D
    {
        this.isVisible = isVisible
        return this
    }

    override fun setTextureScale(scale: Float): Surface2D
    {
        if (scale != textureScale)
        {
            textureScale = scale
            renderTarget.textureScale = scale
            renderTarget.init(width, height)
        }
        return this
    }

    override fun addPostProcessingEffect(effect: PostProcessingEffect): Surface2D
    {
        effect.init()
        postProcessingPipeline.addEffect(effect)
        return this
    }

    override fun removePostProcessingEffect(effect: PostProcessingEffect): Surface2D
    {
        postProcessingPipeline.removeEffect(effect)
        return this
    }

    override fun reloadPostProcessingShaders()
    {
        postProcessingPipeline.reloadShaders()
    }

    override fun addAndInitializeRenderer(renderer: BatchRenderer): Surface2D
    {
        renderers.add(renderer)
        renderer.init()
        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : BatchRenderer> getRenderer(type: KClass<T>): T? =
        renderers.firstOrNullFast { it::class == type } as? T

    override fun getTexture(): Texture =
        postProcessingPipeline.process(renderTarget.getTexture())

    companion object
    {
        private fun createRenderTarget(textureScale: Float, renderState: RenderState) = when (renderState.antiAliasing)
        {
            NONE -> OffScreenRenderTarget(textureScale, renderState.hdrEnabled)
            else -> MultisampledOffScreenRenderTarget(textureScale, renderState.hdrEnabled, renderState.antiAliasing)
        }

        fun create(
            name: String,
            zOrder: Int,
            initCapacity: Int,
            textureArray: TextureArray,
            camera: CameraInternal,
            antiAliasing: AntiAliasingType = NONE,
            hdrEnabled: Boolean = false
        ): Surface2DImpl {
            val renderState = RenderState(antiAliasing = antiAliasing, hdrEnabled = hdrEnabled)
            return Surface2DImpl(
                name = name,
                zOrder = zOrder,
                camera = camera,
                renderState = renderState,
                textRenderer = TextRenderer(),
                quadRenderer = QuadBatchRenderer(initCapacity, renderState),
                lineRenderer = LineBatchRenderer(initCapacity, renderState),
                textureRenderer = TextureBatchRenderer(initCapacity, renderState, textureArray)
            )
        }
    }
}