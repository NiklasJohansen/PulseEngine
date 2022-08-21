package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.StencilState.Action.CLEAR
import no.njoh.pulseengine.core.graphics.StencilState.Action.SET
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.api.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingPipeline
import no.njoh.pulseengine.core.graphics.renderers.*
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import java.lang.RuntimeException
import kotlin.reflect.KClass

interface Surface
{
    val name: String
    val width: Int
    val height: Int

    fun getTexture(index: Int = 0): Texture
    fun getTextures(): List<Texture>
}

abstract class Surface2D : Surface
{
    abstract val camera: Camera
    abstract val context: RenderContext

    // Drawing
    abstract fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    abstract fun drawLineVertex(x: Float, y: Float)
    abstract fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    abstract fun drawQuadVertex(x: Float, y: Float)
    abstract fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, cornerRadius: Float = 0f)
    abstract fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, cornerRadius: Float = 0f, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f, uTiling: Float = 1f, vTiling: Float = 1f)
    abstract fun drawText(text: String, x: Float, y: Float, font: Font? = null, fontSize: Float = -1f, xOrigin: Float = 0f, yOrigin: Float = 0f)

    // Property setters
    abstract fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float = 1f): Surface2D
    abstract fun setDrawColor(color: Color): Surface2D
    abstract fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float = 0f): Surface2D
    abstract fun setBackgroundColor(color: Color): Surface2D
    abstract fun setBlendFunction(func: BlendFunction): Surface2D
    abstract fun setMultisampling(multisampling: Multisampling): Surface2D
    abstract fun setIsVisible(isVisible: Boolean): Surface2D
    abstract fun setTextureFormat(format: TextureFormat): Surface2D
    abstract fun setTextureFilter(filter: TextureFilter): Surface2D
    abstract fun setTextureScale(scale: Float): Surface2D

    // Post-processing
    abstract fun addPostProcessingEffect(effect: PostProcessingEffect): Surface2D
    abstract fun removePostProcessingEffect(effect: PostProcessingEffect): Surface2D

    // Renderers and render state
    abstract fun setRenderState(state: RenderState)
    abstract fun addRenderer(renderer: BatchRenderer): Surface2D
    abstract fun getRenderers(): List<BatchRenderer>
    abstract fun <T: BatchRenderer> getRenderer(type: KClass<T>): T?

    // Clipping/stencil masking
    inline fun drawWithin(x: Float, y: Float, width: Float, height: Float, drawFunc: () -> Unit)
    {
        setRenderState(StencilState(x, y, width, height, action = SET))
        drawFunc()
        setRenderState(StencilState(x, y, width, height, action = CLEAR))
    }
}

abstract class Surface2DInternal : Surface2D()
{
    abstract override val camera: CameraInternal
    abstract override val context: RenderContextInternal

    abstract fun init(width: Int, height: Int, glContextRecreated: Boolean)
    abstract fun initFrame()
    abstract fun renderToOffScreenTarget()
    abstract fun runPostProcessingPipeline()
    abstract fun cleanUp()
}

class Surface2DImpl(
    override val name: String,
    override val camera: CameraInternal,
    override val context: RenderContextInternal,
    private val textRenderer: TextRenderer,
    private val quadRenderer: QuadBatchRenderer,
    private val lineRenderer: LineBatchRenderer,
    private val bindlessTextureRenderer: BindlessTextureRenderer,
    private val textureRenderer: TextureRenderer,
    private val stencilRenderer: StencilRenderer,
): Surface2DInternal() {

    override var width = 0
    override var height = 0
    private var renderTarget = createRenderTarget(context)
    private val postProcessingPipeline = PostProcessingPipeline()
    private var renderStates = Array<RenderState?>(BatchRenderer.MAX_BATCH_COUNT) { null }
    private var batchCount = 0
    private val renderers = mutableListOf(
        lineRenderer,
        bindlessTextureRenderer,
        textureRenderer,
        quadRenderer,
        stencilRenderer
    )

    // Internal functions
    //--------------------------------------------------------------------------------------------

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

    override fun initFrame()
    {
        batchCount = 0
        renderers.forEachFast { it.initBatch() }
        setRenderState(BaseState)
    }

    override fun renderToOffScreenTarget()
    {
        renderTarget.begin()

        for (batchNum in 0 until batchCount)
        {
            // Apply render state for current batch if set, then remove it
            renderStates[batchNum]?.apply(this)
            renderStates[batchNum] = null

            // Render current batch
            renderers.forEachFast { it.renderBatch(this, batchNum) }
        }

        renderTarget.end()
        context.resetDepth(camera.nearPlane)
    }

    override fun runPostProcessingPipeline()
    {
        renderTarget.getTexture()?.let { postProcessingPipeline.process(it) }
    }

    override fun cleanUp()
    {
        renderers.forEachFast { it.cleanUp() }
        renderTarget.cleanUp()
        postProcessingPipeline.cleanUp()
    }

    // Exposed draw functions
    //------------------------------------------------------------------------------------------------

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float) =
        lineRenderer.line(x0, y0, x1, y1)

    override fun drawLineVertex(x: Float, y: Float) =
        lineRenderer.lineVertex(x, y)

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float) =
        quadRenderer.quad(x, y, width, height)

    override fun drawQuadVertex(x: Float, y: Float) =
        quadRenderer.vertex(x, y)

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float)
    {
        if (texture.isBindless)
            bindlessTextureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin, cornerRadius)
        else
            textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float, uTiling: Float, vTiling: Float)
    {
        if (texture.isBindless)
            bindlessTextureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin, cornerRadius, uMin, vMin, uMax, vMax, uTiling, vTiling)
        else
            textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)
    }

    override fun drawText(text: String, x: Float, y: Float, font: Font?, fontSize: Float, xOrigin: Float, yOrigin: Float) =
        textRenderer.draw(this, text, x, y, font ?: Font.DEFAULT, fontSize, xOrigin, yOrigin)

    // Exposed getters
    //------------------------------------------------------------------------------------------------

    override fun getTexture(index: Int): Texture =
        postProcessingPipeline.getFinalTexture()
            ?: renderTarget.getTexture(index)
            ?: throw RuntimeException("Failed to get texture with index: $index from surface with name: $name. " +
                "Surface has the following output specification: ${context.attachments})")

    override fun getTextures(): List<Texture> =
        renderTarget.getTextures()

    @Suppress("UNCHECKED_CAST")
    override fun <T : BatchRenderer> getRenderer(type: KClass<T>): T? =
        renderers.firstOrNullFast { it::class == type } as? T

    override fun getRenderers(): List<BatchRenderer>
    {
        return renderers
    }

    // Exposed setters
    //------------------------------------------------------------------------------------------------

    override fun setIsVisible(isVisible: Boolean): Surface2D
    {
        context.isVisible = isVisible
        return this
    }

    override fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float): Surface2D
    {
        context.setDrawColor(red, green, blue, alpha)
        return this
    }

    override fun setDrawColor(color: Color): Surface2D
    {
        context.setDrawColor(color.red, color.green, color.blue, color.alpha)
        return this
    }

    override fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float): Surface2D
    {
        context.backgroundColor.setFrom(red, green, blue, alpha)
        return this
    }

    override fun setBackgroundColor(color: Color): Surface2D
    {
        context.backgroundColor.setFrom(color)
        return this
    }

    override fun setBlendFunction(func: BlendFunction): Surface2D
    {
        context.blendFunction = func
        return this
    }

    override fun setMultisampling(multisampling: Multisampling): Surface2D
    {
        if (multisampling != context.multisampling)
        {
            context.multisampling = multisampling
            renderTarget.cleanUp()
            renderTarget = createRenderTarget(context)
            renderTarget.init(width, height)
        }
        return this
    }

    private fun createRenderTarget(context: RenderContext) = when (context.multisampling)
    {
        NONE -> OffScreenRenderTarget(context.textureScale, context.textureFormat, context.textureFilter, context.attachments)
        else -> MultisampledOffScreenRenderTarget(context.textureScale, context.textureFormat, context.textureFilter, context.multisampling, context.attachments)
    }

    override fun setTextureFormat(format: TextureFormat): Surface2D
    {
        if (format != context.textureFormat)
        {
            context.textureFormat = format
            renderTarget.textureFormat = format
            renderTarget.init(width, height)
        }
        return this
    }

    override fun setTextureFilter(filter: TextureFilter): Surface2D
    {
        if (filter != context.textureFilter)
        {
            context.textureFilter = filter
            renderTarget.textureFilter = filter
            renderTarget.init(width, height)
        }
        return this
    }

    override fun setTextureScale(scale: Float): Surface2D
    {
        if (scale != context.textureScale)
        {
            context.textureScale = scale
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

    override fun setRenderState(state: RenderState)
    {
        if (batchCount == BatchRenderer.MAX_BATCH_COUNT)
        {
            Logger.error("Reached max batch count of ${BatchRenderer.MAX_BATCH_COUNT}")
            return
        }
        renderers.forEachFast { it.setBatchNumber(batchCount) }
        renderStates[batchCount] = state
        batchCount++
    }

    override fun addRenderer(renderer: BatchRenderer): Surface2D
    {
        renderers.add(renderer)
        renderer.init()
        return this
    }
}