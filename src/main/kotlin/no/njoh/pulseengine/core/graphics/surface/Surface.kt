package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.StencilState.Action.CLEAR
import no.njoh.pulseengine.core.graphics.api.StencilState.Action.SET
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer
import no.njoh.pulseengine.core.shared.primitives.Color

abstract class Surface
{
    abstract val name: String
    abstract val width: Int
    abstract val height: Int
    abstract val camera: Camera
    abstract val config: SurfaceConfig

    // Drawing
    abstract fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    abstract fun drawLineVertex(x: Float, y: Float)
    abstract fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    abstract fun drawQuadVertex(x: Float, y: Float)
    abstract fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, cornerRadius: Float = 0f)
    abstract fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, cornerRadius: Float = 0f, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f, uTiling: Float = 1f, vTiling: Float = 1f)
    abstract fun drawText(text: CharSequence, x: Float, y: Float, font: Font? = null, fontSize: Float = 20f, angle: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f)
    inline fun drawText(textBuilder: (builder: StringBuilder) -> CharSequence, x: Float, y: Float, font: Font? = null, fontSize: Float = 20f, angle: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f) =
        drawText(textBuilder(sb.clear()), x, y, font, fontSize, angle, xOrigin, yOrigin)

    // Property setters
    abstract fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float = 1f): Surface
    abstract fun setDrawColor(color: Color): Surface
    abstract fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float = 0f): Surface
    abstract fun setBackgroundColor(color: Color): Surface
    abstract fun setBlendFunction(func: BlendFunction): Surface
    abstract fun setMultisampling(multisampling: Multisampling): Surface
    abstract fun setIsVisible(isVisible: Boolean): Surface
    abstract fun setTextureFormat(format: TextureFormat): Surface
    abstract fun setTextureFilter(filter: TextureFilter): Surface
    abstract fun setTextureScale(scale: Float): Surface

    // Texture
    abstract fun getTexture(index: Int = 0): Texture
    abstract fun getTextures(): List<Texture>

    // Post-processing
    abstract fun addPostProcessingEffect(effect: PostProcessingEffect)
    abstract fun getPostProcessingEffect(name: String): PostProcessingEffect?
    abstract fun deletePostProcessingEffect(name: String)

    // Renderers and render state
    abstract fun setRenderState(state: RenderState)
    abstract fun addRenderer(renderer: BatchRenderer)
    abstract fun getRenderers(): List<BatchRenderer>
    abstract fun <T: BatchRenderer> getRenderer(type: Class<T>): T?
    inline fun <reified T: BatchRenderer> getRenderer(): T? = getRenderer(T::class.java)

    // Clipping/stencil masking
    inline fun drawWithin(x: Float, y: Float, width: Float, height: Float, drawFunc: () -> Unit)
    {
        setRenderState(StencilState(x, y, width, height, action = SET))
        drawFunc()
        setRenderState(StencilState(x, y, width, height, action = CLEAR))
    }

    // Reusable StringBuilder for text drawing
    @PublishedApi internal val sb = StringBuilder(1000)
}

abstract class SurfaceInternal : Surface()
{
    abstract override val camera: CameraInternal
    abstract override val config: SurfaceConfigInternal

    abstract val renderTarget: RenderTarget

    abstract fun init(width: Int, height: Int, glContextRecreated: Boolean)
    abstract fun initFrame()
    abstract fun renderToOffScreenTarget()
    abstract fun runPostProcessingPipeline()
    abstract fun cleanUp()
}