package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.modules.asset.types.Font
import no.njoh.pulseengine.modules.asset.types.Texture
import no.njoh.pulseengine.modules.graphics.api.AntiAliasing
import no.njoh.pulseengine.modules.graphics.api.AntiAliasing.NONE
import no.njoh.pulseengine.modules.graphics.api.BlendFunction
import no.njoh.pulseengine.modules.graphics.api.TextureFilter
import no.njoh.pulseengine.modules.graphics.api.TextureFormat
import no.njoh.pulseengine.modules.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.modules.graphics.postprocessing.PostProcessingPipeline
import no.njoh.pulseengine.modules.graphics.renderers.*
import no.njoh.pulseengine.util.firstOrNullFast
import no.njoh.pulseengine.util.forEachFast
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.glDrawBuffers
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

interface Surface2D : Surface
{
    val camera: Camera
    val context: RenderContext

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
    fun setAntiAliasingType(antiAliasing: AntiAliasing): Surface2D
    fun setIsVisible(isVisible: Boolean): Surface2D
    fun setTextureFormat(format: TextureFormat): Surface2D
    fun setTextureFilter(filter: TextureFilter): Surface2D
    fun setTextureScale(scale: Float): Surface2D

    // Post processing
    fun addPostProcessingEffect(effect: PostProcessingEffect): Surface2D
    fun removePostProcessingEffect(effect: PostProcessingEffect): Surface2D
    fun reloadPostProcessingShaders()

    // Renderers
    fun addRenderer(renderer: BatchRenderer): Surface2D
    fun <T: BatchRenderer> getRenderer(type: KClass<T>): T?
}

interface Surface2DInternal : Surface2D
{
    override val camera: CameraInternal
    override val context: RenderContextInternal

    fun init(width: Int, height: Int, glContextRecreated: Boolean)
    fun renderToOffScreenTarget()
    fun runPostProcessingPipeline()
    fun cleanUp()
}

class Surface2DImpl(
    override val name: String,
    override val camera: CameraInternal,
    override val context: RenderContextInternal,
    private val textRenderer: TextRenderer,
    private val quadRenderer: QuadBatchRenderer,
    private val lineRenderer: LineBatchRenderer,
    private val bindlessTextureRenderer: BindlessTextureRenderer,
    private val textureRenderer: TextureRenderer
): Surface2DInternal {

    override var width = 0
    override var height = 0
    private var renderTarget = createRenderTarget(context)
    private val postProcessingPipeline = PostProcessingPipeline()
    private val renderers = mutableListOf(lineRenderer, bindlessTextureRenderer, textureRenderer, quadRenderer)

    //////////////////////////////////////
    ////////////// INTERNAL //////////////
    //////////////////////////////////////

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

    override fun renderToOffScreenTarget()
    {
        renderTarget.begin()
        setOpenGlState()
        renderers.forEachFast { it.render(this) }
        renderTarget.end()
        context.resetDepth(camera.nearPlane)
    }

    private fun setOpenGlState()
    {
        // Set depth state
        if (context.hasDepthAttachment)
        {
            glEnable(GL_DEPTH_TEST)
            glDepthMask(true)
            glDepthFunc(GL_LEQUAL)
            glDepthRange(camera.nearPlane.toDouble(), camera.farPlane.toDouble())
            glClearDepth(camera.farPlane.toDouble())
        }
        else glDisable(GL_DEPTH_TEST)

        // Set blending options
        if (context.blendFunction != BlendFunction.NONE)
        {
            glEnable(GL_BLEND)
            glBlendFunc(context.blendFunction.src, context.blendFunction.dest)
        }
        else glDisable(GL_BLEND)

        // Set which attachments from the fragment shader data will be written to
        glDrawBuffers(context.textureAttachments)

        // Set viewport size
        glViewport(0, 0, (width * context.textureScale).toInt(), (height * context.textureScale).toInt())

        // Set color and clear surface
        val c = context.backgroundColor
        glClearColor(c.red, c.green, c.blue, c.alpha)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    private fun createRenderTarget(context: RenderContext) = when (context.antiAliasing)
    {
        NONE -> OffScreenRenderTarget(context.textureScale, context.textureFormat, context.textureFilter, context.attachments)
        else -> MultisampledOffScreenRenderTarget(context.textureScale, context.textureFormat, context.textureFilter, context.antiAliasing, context.attachments)
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

    //////////////////////////////////////////////////////
    ////////////// PUBLIC DRAWING FUNCTIONS //////////////
    //////////////////////////////////////////////////////

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float) =
        lineRenderer.line(x0, y0, x1, y1)

    override fun drawLineVertex(x: Float, y: Float) =
        lineRenderer.lineVertex(x, y)

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float) =
        quadRenderer.quad(x, y, width, height)

    override fun drawQuadVertex(x: Float, y: Float) =
        quadRenderer.vertex(x, y)

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        if (texture.isBindless)
            bindlessTextureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)
        else
            textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        if (texture.isBindless)
            bindlessTextureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin, uMin, vMin, uMax, vMax)
        else
            textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)
    }

    override fun drawText(text: String, x: Float, y: Float, font: Font?, fontSize: Float, xOrigin: Float, yOrigin: Float) =
        textRenderer.draw(this, text, x, y, font ?: Font.DEFAULT, fontSize, xOrigin, yOrigin)

    ////////////////////////////////////////////
    ////////////// PUBLIC GETTERS //////////////
    ////////////////////////////////////////////

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

    ////////////////////////////////////////////
    ////////////// PUBLIC SETTERS //////////////
    ////////////////////////////////////////////

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

    override fun setAntiAliasingType(antiAliasing: AntiAliasing): Surface2D
    {
        if (antiAliasing != context.antiAliasing)
        {
            context.antiAliasing = antiAliasing
            renderTarget.cleanUp()
            renderTarget = createRenderTarget(context)
            renderTarget.init(width, height)
        }
        return this
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

    override fun reloadPostProcessingShaders()
    {
        postProcessingPipeline.reloadShaders()
    }

    override fun addRenderer(renderer: BatchRenderer): Surface2D
    {
        renderers.add(renderer)
        renderer.init()
        return this
    }
}