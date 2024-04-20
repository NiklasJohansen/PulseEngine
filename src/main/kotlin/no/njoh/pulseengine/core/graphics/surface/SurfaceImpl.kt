package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingPipeline
import no.njoh.pulseengine.core.graphics.renderers.*
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer.Companion.MAX_BATCH_COUNT
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import java.lang.RuntimeException

class SurfaceImpl(
//    override val name: String,
//    override var width: Int,
//    override var height: Int,
    override val camera: CameraInternal,
    override val config: SurfaceConfigInternal,
    val textureBank: TextureBank
): SurfaceInternal() {

    override var renderTarget           = createRenderTarget(config)

    private var initialized             = false
    private val postProcessingPipeline  = PostProcessingPipeline()
    private var readRenderStates        = ArrayList<RenderState>(MAX_BATCH_COUNT)
    private var writeRenderStates       = ArrayList<RenderState>(MAX_BATCH_COUNT)
    private var initFrameCommands       = ArrayList<() -> Unit>()

    private var renderers               = ArrayList<BatchRenderer>()
    private var rendererMap             = HashMap<Class<out BatchRenderer>, BatchRenderer>()
    private var textRenderer            = null as TextRenderer?
    private var quadRenderer            = null as QuadBatchRenderer?
    private var lineRenderer            = null as LineBatchRenderer?
    private var textureRenderer         = null as TextureRenderer?
    private var stencilRenderer         = null as StencilRenderer?
    private var bindlessTextureRenderer = null as BindlessTextureRenderer?

    // Internal functions
    //--------------------------------------------------------------------------------------------

    override fun init(width: Int, height: Int, glContextRecreated: Boolean)
    {
        config.width = width
        config.height = height

        if (!initialized)
        {
            textRenderer            = TextRenderer(config, textureBank)
            quadRenderer            = QuadBatchRenderer(config)
            lineRenderer            = LineBatchRenderer(config)
            textureRenderer         = TextureRenderer(config)
            stencilRenderer         = StencilRenderer()
            bindlessTextureRenderer = BindlessTextureRenderer(config, textureBank)
            renderers               += listOfNotNull(textRenderer, quadRenderer, lineRenderer, textureRenderer, stencilRenderer, bindlessTextureRenderer)

            renderers.forEachFast { rendererMap[it::class.java] = it }
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
        config.resetDepth(camera.nearPlane)
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

    override fun destroy()
    {
        renderers.forEachFast { it.destroy() }
        renderTarget.destroy()
        postProcessingPipeline.destroy()
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
                "Failed to get texture with index: $index from surface with name: ${config.name}. " +
                    "Surface has the following output specification: ${config.attachments})"
            )
    }

    override fun getTextures(): List<Texture>
    {
        return renderTarget.getTextures()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : BatchRenderer> getRenderer(type: Class<T>): T?
    {
        return rendererMap[type] as T?
    }

    override fun getRenderers(): List<BatchRenderer>
    {
        return renderers
    }

    // Exposed setters
    //------------------------------------------------------------------------------------------------

    override fun setIsVisible(isVisible: Boolean): Surface
    {
        config.isVisible = isVisible
        return this
    }

    override fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float): Surface
    {
        config.setDrawColor(red, green, blue, alpha)
        return this
    }

    override fun setDrawColor(color: Color): Surface
    {
        config.setDrawColor(color.red, color.green, color.blue, color.alpha)
        return this
    }

    override fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float): Surface
    {
        config.backgroundColor.setFrom(red, green, blue, alpha)
        return this
    }

    override fun setBackgroundColor(color: Color): Surface
    {
        config.backgroundColor.setFrom(color)
        return this
    }

    override fun setBlendFunction(func: BlendFunction): Surface
    {
        config.blendFunction = func
        return this
    }

    override fun setMultisampling(multisampling: Multisampling): Surface
    {
        if (multisampling != config.multisampling)
        {
            config.multisampling = multisampling
            runOnInitFrame()
            {
                renderTarget.destroy()
                renderTarget = createRenderTarget(config)
                renderTarget.init(config.width, config.height)
            }
        }
        return this
    }

    private fun createRenderTarget(config: SurfaceConfig) = when (config.multisampling)
    {
        Multisampling.NONE -> OffScreenRenderTarget(config.textureScale, config.textureFormat, config.textureFilter, config.attachments)
        else -> MultisampledOffScreenRenderTarget(config.textureScale, config.textureFormat, config.textureFilter, config.multisampling, config.attachments)
    }

    override fun setTextureFormat(format: TextureFormat): Surface
    {
        if (format != config.textureFormat)
        {
            config.textureFormat = format
            renderTarget.textureFormat = format
            runOnInitFrame { renderTarget.init(config.width, config.height) }
        }
        return this
    }

    override fun setTextureFilter(filter: TextureFilter): Surface
    {
        if (filter != config.textureFilter)
        {
            config.textureFilter = filter
            renderTarget.textureFilter = filter
            runOnInitFrame { renderTarget.init(config.width, config.height) }
        }
        return this
    }

    override fun setTextureScale(scale: Float): Surface
    {
        if (scale != config.textureScale)
        {
            config.textureScale = scale
            renderTarget.textureScale = scale
            runOnInitFrame { renderTarget.init(config.width, config.height) }
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
            effect?.destroy()
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
            rendererMap[renderer.javaClass] = renderer
        }
    }

    private fun runOnInitFrame(command: () -> Unit) { initFrameCommands.add(command) }
}