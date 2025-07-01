package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.core.graphics.renderers.*
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer.Companion.MAX_BATCH_COUNT
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachReversed
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import no.njoh.pulseengine.core.shared.utils.Logger

class SurfaceImpl(
    override val camera: CameraInternal,
    override val config: SurfaceConfigInternal,
): SurfaceInternal() {

    override var renderTarget         = createRenderTarget(config)
    private var initialized           = false
    private var shouldRerender        = false
    private val onInitFrame           = ArrayList<(PulseEngineInternal) -> Unit>()
    private var readRenderStates      = ArrayList<RenderState>(MAX_BATCH_COUNT)
    private var writeRenderStates     = ArrayList<RenderState>(MAX_BATCH_COUNT)
    private val postEffects           = ArrayList<PostProcessingEffect>()
    private val renderers             = ArrayList<BatchRenderer>()
    private val rendererMap           = HashMap<Class<out BatchRenderer>, BatchRenderer>()
    private var textRenderer          = null as TextRenderer?
    private var quadRenderer          = null as QuadRenderer?
    private var lineRenderer          = null as LineRenderer?
    private var textureRenderer       = null as TextureRenderer?
    private var stencilRenderer       = null as StencilRenderer?
    private var renderTextureRenderer = null as RenderTextureRenderer?

    // Internal functions
    //--------------------------------------------------------------------------------------------

    override fun init(engine: PulseEngineInternal, width: Int, height: Int, glContextRecreated: Boolean)
    {
        config.width = width
        config.height = height

        if (!initialized)
        {
            textRenderer          = TextRenderer(config)
            quadRenderer          = QuadRenderer(config)
            lineRenderer          = LineRenderer(config)
            textureRenderer       = TextureRenderer(config)
            stencilRenderer       = StencilRenderer()
            renderTextureRenderer = RenderTextureRenderer(config)
            renderers             += listOfNotNull(textRenderer, quadRenderer, lineRenderer, textureRenderer, stencilRenderer, renderTextureRenderer)

            renderers.forEachFast { rendererMap[it::class.java] = it }
        }

        if (glContextRecreated || !initialized)
        {
            renderers.forEachFast { it.init(engine) }
            postEffects.forEachFast { it.init(engine) }
            config.mipmapGenerator?.init(engine)
        }

        renderTarget.init(width, height)
        shouldRerender = true
        initialized = true
    }

    override fun initFrame(engine: PulseEngineInternal)
    {
        readRenderStates = writeRenderStates.also { writeRenderStates = readRenderStates }
        writeRenderStates.clear()

        onInitFrame.forEachFast { it.invoke(engine) }
        onInitFrame.clear()

        renderers.forEachFast { it.initFrame() }
        config.resetDepth(camera.nearPlane)
        applyRenderState(BatchRenderBaseState)
    }

    override fun renderToOffScreenTarget(engine: PulseEngineInternal)
    {
        renderTarget.begin()

        var batchNum = 0
        while (batchNum < readRenderStates.size)
        {
            readRenderStates[batchNum].apply(this)
            GpuProfiler.measure({ "RENDER_BATCH " plus " (#" plus batchNum plus ")" })
            {
                renderers.forEachFast { it.renderBatch(engine, this, batchNum) }
            }
            batchNum++
        }

        renderTarget.end()
        renderTarget.generateMips(engine)

        if (batchNum > 0) shouldRerender = false // Something was rendered, clear flag
    }

    override fun runPostProcessingPipeline(engine: PulseEngineInternal)
    {
        if (postEffects.isEmpty()) return // No post-processing effects to run

        // Make sure the view port is set to the same size as the scaled surface texture
        ViewportState.apply(this)

        var textures = renderTarget.getTextures()
        postEffects.forEachFast()
        {
            GpuProfiler.measure(label = { "EFFECT (" plus it.name plus ")" })
            {
                textures = it.process(engine, textures)
            }
        }
    }

    override fun destroy()
    {
        renderers.forEachFast { it.destroy() }
        postEffects.forEachFast { it.destroy() }
        renderTarget.destroy()
        config.mipmapGenerator?.destroy()
    }

    override fun hasContent() = shouldRerender || renderers.anyMatches { it.hasContentToRender() }

    override fun hasPostProcessingEffects() = postEffects.isNotEmpty()

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

    override fun drawTexture(texture: RenderTexture, x: Float, y: Float, width: Float, height: Float, angle: Degrees, xOrigin: Float, yOrigin: Float)
    {
        renderTextureRenderer?.draw(texture, x, y, width, height, angle, xOrigin, yOrigin)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, angle: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float)
    {
        textureRenderer?.draw(texture, x, y, width, height, angle, xOrigin, yOrigin, cornerRadius)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, angle: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float, uTiling: Float, vTiling: Float)
    {
        textureRenderer?.draw(texture, x, y, width, height, angle, xOrigin, yOrigin, cornerRadius, uMin, vMin, uMax, vMax, uTiling, vTiling)
    }

    override fun drawText(text: CharSequence, x: Float, y: Float, font: Font?, fontSize: Float, angle: Float, xOrigin: Float, yOrigin: Float)
    {
        textRenderer?.draw(text, x, y, font ?: Font.DEFAULT, fontSize, angle, xOrigin, yOrigin)
    }

    // Exposed getters
    //------------------------------------------------------------------------------------------------

    override fun getTexture(index: Int, final: Boolean): RenderTexture
    {
        if (final) postEffects.forEachReversed { effect -> effect.getTexture(index)?.let { return it } }

        return renderTarget.getTexture(index)
            ?: throw RuntimeException(
                "Failed to get texture with index: $index from surface with name: ${config.name}. " +
                    "Surface has the following output specification: ${config.attachments})"
            )
    }

    override fun getTextures(): List<RenderTexture>
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
        val bgColor = config.backgroundColor
        if (red != bgColor.red || green != bgColor.green || blue != bgColor.blue || alpha != alpha)
            shouldRerender = true
        bgColor.setFromRgba(red, green, blue, alpha)
        return this
    }

    override fun setBackgroundColor(color: Color): Surface
    {
        if (config.backgroundColor != color)
            shouldRerender = true
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
                renderTarget.init(this@SurfaceImpl.config.width, config.height)
            }
        }
        return this
    }

    private fun createRenderTarget(config: SurfaceConfig) = RenderTarget(
        textureDescriptors = config.attachments.map { attachment ->
            TextureDescriptor(
                format = config.textureFormat,
                filter = config.textureFilter,
                wrapping = TextureWrapping.CLAMP_TO_EDGE,
                multisampling = config.multisampling,
                attachment = attachment,
                scale = config.textureScale,
                sizeFunc = config.textureSizeFunc,
                mipmapGenerator = config.mipmapGenerator,
            )
        }
    )

    override fun setTextureFormat(format: TextureFormat): Surface
    {
        if (format != config.textureFormat)
        {
            config.textureFormat = format
            renderTarget.textureDescriptors.forEachFast { it.format = format }
            runOnInitFrame { renderTarget.init(config.width, config.height) }
        }
        return this
    }

    override fun setTextureFilter(filter: TextureFilter): Surface
    {
        if (filter != config.textureFilter)
        {
            config.textureFilter = filter
            renderTarget.textureDescriptors.forEachFast { it.filter = filter }
            runOnInitFrame { renderTarget.init(config.width, config.height) }
        }
        return this
    }

    override fun setTextureScale(scale: Float): Surface
    {
        if (scale != config.textureScale)
        {
            config.textureScale = scale
            renderTarget.textureDescriptors.forEachFast { it.scale = scale }
            runOnInitFrame { renderTarget.init(config.width, config.height) }
        }
        return this
    }

    override fun addPostProcessingEffect(effect: PostProcessingEffect)
    {
        runOnInitFrame { engine ->
            getPostProcessingEffect(effect.name)?.let()
            {
                Logger.warn { "Replacing existing post processing effect with same name: ${it.name}" }
                deletePostProcessingEffect(it.name)
            }
            effect.init(engine)
            postEffects.add(effect)
            postEffects.sortBy { it.order }
        }
    }

    override fun getPostProcessingEffects(): List<PostProcessingEffect>
    {
        return postEffects
    }

    override fun getPostProcessingEffect(name: String): PostProcessingEffect?
    {
        return postEffects.firstOrNullFast { it.name == name }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : PostProcessingEffect> getPostProcessingEffect(type: Class<T>): T?
    {
        return postEffects.firstOrNullFast { type.isAssignableFrom(it.javaClass) } as T?
    }

    override fun deletePostProcessingEffect(name: String)
    {
        runOnInitFrame()
        {
            getPostProcessingEffect(name)?.destroy()
            postEffects.removeWhen { it.name == name }
        }
    }

    override fun applyRenderState(state: RenderState)
    {
        if (writeRenderStates.size >= MAX_BATCH_COUNT)
        {
            Logger.error { "Reached max batch count of $MAX_BATCH_COUNT" }
            return
        }

        // Finnish current batch if new render states are added after the base state
        if (writeRenderStates.size > 0)
            renderers.forEachFast { it.finishCurrentBatch() }

        writeRenderStates.add(state)
    }

    override fun addRenderer(renderer: BatchRenderer)
    {
        runOnInitFrame { engine ->
            renderer.init(engine)
            renderers.add(renderer)
            rendererMap[renderer.javaClass] = renderer
        }
    }

    private fun runOnInitFrame(command: (PulseEngineInternal) -> Unit) { onInitFrame.add(command) }
}