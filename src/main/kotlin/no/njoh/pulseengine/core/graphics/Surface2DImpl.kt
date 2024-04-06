package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.BlendFunction
import no.njoh.pulseengine.core.graphics.api.Multisampling
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingPipeline
import no.njoh.pulseengine.core.graphics.renderers.*
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer.Companion.MAX_BATCH_COUNT
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import java.lang.RuntimeException
import kotlin.reflect.KClass

class Surface2DImpl(
    override val name: String,
    override var width: Int,
    override var height: Int,
    override val camera: CameraInternal,
    override val context: RenderContextInternal,
    val textureBank: TextureBank
): Surface2DInternal() {

    override var renderTarget = createRenderTarget(context)

    private var initialized = false
    private val postProcessingPipeline = PostProcessingPipeline()
    private var readRenderStates = ArrayList<RenderState>(MAX_BATCH_COUNT)
    private var writeRenderStates = ArrayList<RenderState>(MAX_BATCH_COUNT)
    private var initFrameCommands = mutableListOf<() -> Unit>()

    private var textRenderer: TextRenderer? = null
    private var quadRenderer: QuadBatchRenderer? = null
    private var lineRenderer: LineBatchRenderer? = null
    private var bindlessTextureRenderer: BindlessTextureRenderer? = null
    private var textureRenderer: TextureRenderer? = null
    private var stencilRenderer: StencilRenderer? = null
    private var renderers = mutableListOf<BatchRenderer>()

    // Internal functions
    //--------------------------------------------------------------------------------------------

    override fun init(width: Int, height: Int, glContextRecreated: Boolean)
    {
        this.width = width
        this.height = height

        if (!initialized)
        {
             textRenderer            = TextRenderer(context, textureBank)
             quadRenderer            = QuadBatchRenderer(context)
             lineRenderer            = LineBatchRenderer(context)
             bindlessTextureRenderer = BindlessTextureRenderer(context, textureBank)
             textureRenderer         = TextureRenderer(context)
             stencilRenderer         = StencilRenderer()
             renderers               += listOfNotNull(textRenderer, quadRenderer, lineRenderer, bindlessTextureRenderer, textureRenderer, stencilRenderer)
        }

        if (glContextRecreated || !initialized)
        {
            renderers.forEachFast { it.init() }
            postProcessingPipeline.init()
        }

        renderTarget.init(width, height)
        initialized = true
    }

    override fun initFrame()
    {
        readRenderStates = writeRenderStates.also { writeRenderStates = readRenderStates }
        writeRenderStates.clear()

        initFrameCommands.forEachFast { it.invoke() }
        initFrameCommands.clear()

        renderers.forEachFast { it.initFrame() }
        context.resetDepth(camera.nearPlane)
        setRenderState(BaseState)
    }

    override fun renderToOffScreenTarget()
    {
        renderTarget.begin()

        var batchNum = 0
        while (batchNum < readRenderStates.size)
        {
            readRenderStates[batchNum].apply(this)
            renderers.forEachFast { it.renderBatch(this, batchNum) }
            batchNum++
        }

        renderTarget.end()
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

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        lineRenderer?.line(x0, y0, x1, y1)
    }

    override fun drawLineVertex(x: Float, y: Float)
    {
        lineRenderer?.lineVertex(x, y)
    }

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    {
        quadRenderer?.quad(x, y, width, height)
    }

    override fun drawQuadVertex(x: Float, y: Float)
    {
        quadRenderer?.vertex(x, y)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float)
    {
        if (texture.isBindless)
            bindlessTextureRenderer?.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin, cornerRadius)
        else
            textureRenderer?.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float, uTiling: Float, vTiling: Float)
    {
        if (texture.isBindless)
            bindlessTextureRenderer?.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin, cornerRadius, uMin, vMin, uMax, vMax, uTiling, vTiling)
        else
            textureRenderer?.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)
    }

    override fun drawText(text: CharSequence, x: Float, y: Float, font: Font?, fontSize: Float, angle: Float, xOrigin: Float, yOrigin: Float)
    {
        textRenderer?.draw(text, x, y, font ?: Font.DEFAULT, fontSize, angle, xOrigin, yOrigin)
    }

    // Exposed getters
    //------------------------------------------------------------------------------------------------

    override fun getTexture(index: Int): Texture
    {
        return postProcessingPipeline.getFinalTexture()
            ?: renderTarget.getTexture(index)
            ?: throw RuntimeException(
                "Failed to get texture with index: $index from surface with name: $name. " +
                    "Surface has the following output specification: ${context.attachments})"
            )
    }

    override fun getTextures(): List<Texture>
    {
        return renderTarget.getTextures()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : BatchRenderer> getRenderer(type: KClass<T>): T?
    {
        return renderers.firstOrNull { type.isInstance(it) } as T?
    }

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
            runOnInitFrame()
            {
                renderTarget.cleanUp()
                renderTarget = createRenderTarget(context)
                renderTarget.init(width, height)
            }
        }
        return this
    }

    private fun createRenderTarget(context: RenderContext) = when (context.multisampling)
    {
        Multisampling.NONE -> OffScreenRenderTarget(context.textureScale, context.textureFormat, context.textureFilter, context.attachments)
        else -> MultisampledOffScreenRenderTarget(context.textureScale, context.textureFormat, context.textureFilter, context.multisampling, context.attachments)
    }

    override fun setTextureFormat(format: TextureFormat): Surface2D
    {
        if (format != context.textureFormat)
        {
            context.textureFormat = format
            renderTarget.textureFormat = format
            runOnInitFrame { renderTarget.init(width, height) }
        }
        return this
    }

    override fun setTextureFilter(filter: TextureFilter): Surface2D
    {
        if (filter != context.textureFilter)
        {
            context.textureFilter = filter
            renderTarget.textureFilter = filter
            runOnInitFrame { renderTarget.init(width, height) }
        }
        return this
    }

    override fun setTextureScale(scale: Float): Surface2D
    {
        if (scale != context.textureScale)
        {
            context.textureScale = scale
            renderTarget.textureScale = scale
            runOnInitFrame { renderTarget.init(width, height) }
        }
        return this
    }

    override fun addPostProcessingEffect(effect: PostProcessingEffect)
    {
        runOnInitFrame()
        {
            effect.init()
            postProcessingPipeline.addEffect(effect)
        }
    }

    override fun getPostProcessingEffect(name: String): PostProcessingEffect?
    {
        return postProcessingPipeline.getEffect(name)
    }

    override fun deletePostProcessingEffect(name: String)
    {
        runOnInitFrame()
        {
            val effect = postProcessingPipeline.getEffect(name)
            postProcessingPipeline.removeEffect(name)
            effect?.cleanUp()
        }
    }

    override fun setRenderState(state: RenderState)
    {
        if (writeRenderStates.size >= MAX_BATCH_COUNT)
        {
            Logger.error("Reached max batch count of $MAX_BATCH_COUNT")
            return
        }

        // Finnish current batch if new render states are added after the base state
        if (writeRenderStates.size > 0)
            renderers.forEachFast { it.finishCurrentBatch() }

        writeRenderStates.add(state)
    }
    override fun addRenderer(renderer: BatchRenderer)
    {
        runOnInitFrame()
        {
            renderer.init()
            renderers.add(renderer)
        }
    }

    private fun runOnInitFrame(command: () -> Unit) { initFrameCommands.add(command) }
}