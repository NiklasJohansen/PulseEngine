package no.njoh.pulseengine.core.graphics.surface

import gnu.trove.map.hash.THashMap
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.camera.CameraInternal
import no.njoh.pulseengine.core.graphics.gpu.texture.BlendFunction
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.DEPTH_STENCIL_BUFFER
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureDescriptor
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.CLAMP_TO_EDGE
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer.Companion.MAX_BATCH_COUNT
import no.njoh.pulseengine.core.graphics.surface.renderers.LineRenderer
import no.njoh.pulseengine.core.graphics.surface.renderers.QuadRenderer
import no.njoh.pulseengine.core.graphics.surface.renderers.RenderTextureRenderer
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import no.njoh.pulseengine.core.graphics.surface.renderers.StencilRenderer
import no.njoh.pulseengine.core.graphics.surface.renderers.TextRenderer
import no.njoh.pulseengine.core.graphics.surface.renderers.TextureRenderer
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewGroup
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.PixelReadResult
import no.njoh.pulseengine.core.graphics.util.AsyncPixelReader
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Border
import no.njoh.pulseengine.core.shared.primitives.CornerRadius
import no.njoh.pulseengine.core.shared.primitives.Degrees
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachIndexedFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachReversed
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import no.njoh.pulseengine.core.shared.utils.Logger
import kotlin.text.format

class SurfaceImpl(
    override val camera: CameraInternal,
    override val config: SurfaceConfigInternal,
): SurfaceInternal() {

    override val viewGroup            = RenderViewGroup.create()
    override var renderTarget         = createRenderTarget()

    private var initialized           = false
    private var shouldRerender        = false
    private var pendingTargetRebuild  = false
    private val onInitFrame           = ArrayList<(PulseEngineInternal) -> Unit>()
    private var readRenderStates      = ArrayList<RenderState>(MAX_BATCH_COUNT)
    private var writeRenderStates     = ArrayList<RenderState>(MAX_BATCH_COUNT)
    private val postEffects           = ArrayList<PostProcessingEffect>()
    private val renderers             = ArrayList<Renderer>()
    private val rendererMap           = THashMap<Class<out Renderer>, Renderer>()
    private var textRenderer          = null as TextRenderer?
    private var quadRenderer          = null as QuadRenderer?
    private var lineRenderer          = null as LineRenderer?
    private var textureRenderer       = null as TextureRenderer?
    private var stencilRenderer       = null as StencilRenderer?
    private var renderTextureRenderer = null as RenderTextureRenderer?

    @Volatile 
    private var pixelReaders = emptyArray<AsyncPixelReader?>()

    // Internal functions
    //--------------------------------------------------------------------------------------------

    override fun init(engine: PulseEngineInternal, width: Int, height: Int, glContextRecreated: Boolean)
    {
        config.width = width
        config.height = height

        if (initialized)
            resetPixelReaders()

        if (pendingTargetRebuild)
        {
            if (initialized) 
                renderTarget.destroy()
            renderTarget = createRenderTarget()
            pendingTargetRebuild = false
        }

        if (!initialized)
        {
            textRenderer          = TextRenderer(config)
            quadRenderer          = QuadRenderer(config)
            lineRenderer          = LineRenderer(config)
            textureRenderer       = TextureRenderer(config)
            stencilRenderer       = StencilRenderer()
            renderTextureRenderer = RenderTextureRenderer(config)
            renderers             += listOfNotNull(renderTextureRenderer, textRenderer, quadRenderer, lineRenderer, textureRenderer, stencilRenderer)

            renderers.forEachFast { rendererMap[it::class.java] = it }
        }

        if (glContextRecreated || !initialized)
        {
            renderers.forEachFast { it.init(engine, this) }
            postEffects.forEachFast { it.init(engine) }
            config.attachments.forEachFast { it.mipmapGenerator?.init(engine) }
        }

        renderers.sortBy { it.order }
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

        renderers.forEachFast { it.initFrame(engine, this) }

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
            measure("render_batch", label = { "Render batch: #" plus batchNum })
            {
                renderers.forEachFast { it.render(engine, this, batchNum) }
            }
            batchNum++
        }

        renderTarget.end()
        renderTarget.setColorAlphaMode(config.blendFunction.outputAlphaMode)
        renderTarget.generateMips(engine)

        if (batchNum > 0) shouldRerender = false // Something was rendered, clear flag
    }

    override fun runPostProcessingPipeline(engine: PulseEngineInternal)
    {
        if (!config.drawPostEffects || postEffects.isEmpty()) return

        // Make sure the view port is set to the same size as the scaled surface texture
        ViewportState.apply(this)

        var textures = renderTarget.getTextures()
        postEffects.forEachFast()
        {
            measure(id = it.name, label = { "Effect: " plus it.name})
            {
                textures = it.process(engine, textures)
            }
        }
    }

    override fun pollPixelReads()
    {
        val readers = pixelReaders
        for (slot in 0 until readers.size)
        {
            val reader = readers[slot] ?: continue
            if (!reader.hasPendingWork()) continue
            val textureIndex = slot ushr 1
            val final = (slot and 1) != 0
            val texture = getTexture(textureIndex, final)
            reader.update(texture)
        }
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        resetPixelReaders()
        renderers.forEachFast { it.destroy(engine) }
        postEffects.forEachFast { it.destroy() }
        renderTarget.destroy()
        config.attachments.forEachFast { it.mipmapGenerator?.destroy() }
    }

    override fun hasContent() = shouldRerender || renderers.anyMatches { it.hasContentToRender() }

    override fun hasPendingPixelReads() = pixelReaders.any { it?.hasPendingWork() == true }

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

    override fun drawTexture(texture: RenderTexture, x: Float, y: Float, width: Float, height: Float, angle: Degrees, xOrigin: Float, yOrigin: Float, cornerRadius: CornerRadius, border: Border, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        renderTextureRenderer?.draw(texture, x, y, width, height, angle, xOrigin, yOrigin, cornerRadius, uMin, vMin, uMax, vMax, border)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, angle: Float, xOrigin: Float, yOrigin: Float, cornerRadius: CornerRadius, border: Border)
    {
        textureRenderer?.draw(texture, x, y, width, height, angle, xOrigin, yOrigin, cornerRadius, border)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, angle: Float, xOrigin: Float, yOrigin: Float, cornerRadius: CornerRadius, border: Border, uMin: Float, vMin: Float, uMax: Float, vMax: Float, xTiling: Float, yTiling: Float)
    {
        textureRenderer?.draw(texture, x, y, width, height, angle, xOrigin, yOrigin, cornerRadius, uMin, vMin, uMax, vMax, xTiling, yTiling, border)
    }

    override fun drawText(text: CharSequence, x: Float, y: Float, font: Font?, fontSize: Float, angle: Float, xOrigin: Float, yOrigin: Float, wrapNewLines: Boolean, newLineSpacing: Float)
    {
        textRenderer?.draw(text, x, y, font ?: Font.DEFAULT, fontSize, angle, xOrigin, yOrigin, wrapNewLines, newLineSpacing)
    }

    // Exposed getters
    //------------------------------------------------------------------------------------------------

    override fun getTexture(index: Int, final: Boolean): RenderTexture
    {
        if (final && config.drawPostEffects)
            postEffects.forEachReversed { effect -> effect.getTexture(index)?.let { return it } }

        return renderTarget.getTexture(index) ?: throw RuntimeException(
            "Failed to get texture with index: $index from surface with name: ${config.name}. " +
            "Surface has the following output specification: ${config.attachments})"
        )
    }

    override fun getTexture(attachmentPoint: AttachmentPoint, final: Boolean): RenderTexture
    {
        if (final && attachmentPoint == COLOR_TEXTURE_0)
            return getTexture(0, final = true)

        return renderTarget.getTexture(attachmentPoint) ?: throw RuntimeException(
            "Failed to get texture for attachment point: $attachmentPoint from surface with name: ${config.name}. " +
            "Surface has the following output specification: ${config.attachments}"
        )
    }

    override fun getTextures(): List<RenderTexture>
    {
        return renderTarget.getTextures()
    }

    override fun readPixel(x: Int, y: Int, textureIndex: Int, final: Boolean, dstResult: PixelReadResult): PixelReadResult
    {
        require(textureIndex >= 0) { "Texture index must be non-negative" }
        require(textureIndex < outputTextureCount()) { "Texture index $textureIndex does not exist on surface ${config.name}" }
        
        val slot = textureIndex * 2 + if (final) 1 else 0
        var readers = pixelReaders
        var reader = readers.getOrNull(slot)

        if (reader == null)
        {
            synchronized(this)
            {
                readers = pixelReaders
                reader = readers.getOrNull(slot)
                if (reader == null)
                {
                    reader = AsyncPixelReader()
                    if (slot >= readers.size)
                    {
                        val oldSize = readers.size
                        val newSize = maxOf(4, slot + 1, oldSize * 2)
                        readers = readers.copyOf(newSize)
                    }
                    readers[slot] = reader
                    pixelReaders = readers
                }
            }
        }
        
        return reader!!.readPixel(x, y, dstResult)
    }

    override fun readPixel(x: Int, y: Int, attachmentPoint: AttachmentPoint, final: Boolean, dstResult: PixelReadResult): PixelReadResult
    {
        renderTarget.getTextures().forEachIndexedFast { i, texture ->
            if (texture.attachmentPoint == attachmentPoint)
                return readPixel(x, y, i, final = final && attachmentPoint == COLOR_TEXTURE_0, dstResult)
        }
        throw IllegalArgumentException("Surface ${config.name} has no texture for attachment point $attachmentPoint")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Renderer> getRenderer(type: Class<T>): T?
    {
        return rendererMap[type] as T?
    }

    override fun getAllRenderers(): List<Renderer>
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

    override fun setClearColor(color: Color?): Surface
    {
        if (config.clearColor != color)
            shouldRerender = true
        
        if (color == null)
            config.clearColor = null
        else if (config.clearColor == null)
            config.clearColor = color.copy()
        else 
            config.clearColor!!.setFrom(color)

        return this
    }

    override fun setBlendFunction(func: BlendFunction): Surface
    {
        config.blendFunction = func
        return this
    }

    override fun setMultisampling(multisampling: Multisampling): Surface
    {
        if (config.multisampling == multisampling)
            return this

        config.multisampling = multisampling
        requestRenderTargetRebuild()
        return this
    }

    override fun setAttachment(attachment: SurfaceAttachment): Surface
    {
        val index = attachmentIndexOf(attachment.attachmentPoint)
        if (index >= 0 && config.attachments[index] == attachment)
            return this // No change

        if (initialized)
        {
            val currentGenerator = if (index >= 0) config.attachments[index].mipmapGenerator else null
            require(currentGenerator === attachment.mipmapGenerator) 
            {
                "Mipmap generators are creation-time surface resources and cannot be changed after initialization"
            }
        }

        val attachments = config.attachments.toMutableList()
        if (index >= 0)
        {
            attachments[index] = attachment
        }
        else
        {
            val firstDepth = attachments.indexOfFirst { it.attachmentPoint.isDepth }
            if (attachment.attachmentPoint.isColor && firstDepth >= 0)
            {
                attachments.add(firstDepth, attachment)
            }
            else attachments.add(attachment)
        }

        config.attachments = attachments
        requestRenderTargetRebuild()
        return this
    }

    override fun removeAttachment(attachmentPoint: AttachmentPoint): Surface
    {
        val index = attachmentIndexOf(attachmentPoint)
        if (index < 0) return this

        require(!initialized || config.attachments[index].mipmapGenerator == null) 
        {
            "Attachments with mipmap generators cannot be removed after surface initialization"
        }

        config.attachments = config.attachments.toMutableList().also { it.removeAt(index) }
        requestRenderTargetRebuild()
        return this
    }

    override fun setTextureFormat(format: TextureFormat, attachmentPoint: AttachmentPoint): Surface
    {
        val current = config.getAttachment(attachmentPoint) ?: return this
        return if (current.format == format) this else setAttachment(current.copy(format = format))
    }

    override fun setTextureFilter(filter: TextureFilter, attachmentPoint: AttachmentPoint): Surface
    {
        val current = config.getAttachment(attachmentPoint) ?: return this
        return if (current.filter == filter) this else setAttachment(current.copy(filter = filter))
    }

    override fun setResolutionScale(scale: Float): Surface
    {
        if (config.resolutionScale == scale)
            return this

        require(scale.isFinite() && scale > 0f) { "Surface output resolution scale must be finite and positive" }
        config.resolutionScale = scale
        requestRenderTargetRebuild()
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

    override fun addRenderer(renderer: Renderer)
    {
        runOnInitFrame()
        {
            renderer.init(it, this)
            rendererMap[renderer.javaClass] = renderer
            renderers.add(renderer)
            renderers.sortBy { it.order }
        }
    }

    override fun deleteRenderer(renderer: Renderer)
    {
        runOnInitFrame()
        {
            renderers.remove(renderer)
            rendererMap.remove(renderer.javaClass)
            renderer.destroy(it)
        }
    }

    private fun createRenderTarget(): RenderTarget = RenderTarget(
        textureDescriptors = config.attachments.map()
        {
            TextureDescriptor(
                format = it.format,
                filter = it.filter,
                wrapping = CLAMP_TO_EDGE,
                multisampling = config.multisampling,
                mipmapGenerator = it.mipmapGenerator,
                attachmentPoint = it.attachmentPoint,
                scale = config.resolutionScale,
                sizeFunc = config.sizeFunction
            )
        }
    )

    private fun requestRenderTargetRebuild()
    {
        shouldRerender = true
        if (pendingTargetRebuild) 
            return

        pendingTargetRebuild = true
        if (!initialized)
            return

        runOnInitFrame() 
        {
            if (pendingTargetRebuild)
            {
                resetPixelReaders()
                renderTarget.destroy()
                renderTarget = createRenderTarget()
                renderTarget.init(config.width, config.height)
                pendingTargetRebuild = false
            }
        }
    }

    private fun attachmentIndexOf(attachmentPoint: AttachmentPoint): Int
    {
        config.attachments.forEachIndexedFast { i, att -> if (att.attachmentPoint == attachmentPoint) return i }
        return -1
    }

    private fun outputTextureCount(): Int
    {
        var count = 0
        config.attachments.forEachFast { if (it.attachmentPoint != DEPTH_STENCIL_BUFFER) count++ }
        return count
    }

    private fun resetPixelReaders() = pixelReaders.forEachFast { it?.reset() }

    private fun runOnInitFrame(command: (PulseEngineInternal) -> Unit) { onInitFrame.add(command) }
}
