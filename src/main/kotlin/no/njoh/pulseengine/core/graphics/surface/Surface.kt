package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.StencilState.Action.CLEAR
import no.njoh.pulseengine.core.graphics.api.StencilState.Action.SET
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer
import no.njoh.pulseengine.core.shared.primitives.Color

typealias Degrees = Float

/**
 * A surface is a rendering target that can be used to draw lines, quads, textures and text.
 */
abstract class Surface
{
    /** Holds all the public state and configuration parameters of the [Surface]. */
    abstract val config: SurfaceConfig

    /** The camera used to control where and how everything should be rendered to the [Surface]. */
    abstract val camera: Camera

    ///////////////////////////////////////// Drawing Operations /////////////////////////////////////////

    /**
     * Draws a line from (x0, y0) to (x1, y1).
     */
    abstract fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)

    /**
     * Sets a single line vertex. Call this method twice to draw a line.
     */
    abstract fun drawLineVertex(x: Float, y: Float)

    /**
     * Draws a colored quad with the top left corner at (x, y) and the specified width and height.
     */
    abstract fun drawQuad(x: Float, y: Float, width: Float, height: Float)

    /**
     * Sets a single quad vertex. Call this method four times to draw a full quad.
     */
    abstract fun drawQuadVertex(x: Float, y: Float)

    /**
     * Draws a textured quad at a given position with a given size, rotation, origin and corner radius.
     */
    abstract fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, angle: Degrees = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, cornerRadius: Float = 0f)

    /**
     * Draws a textured quad at a given position with a given size, rotation, origin, corner radius and
     * texture coordinates. The texture will be tiled according to the given tiling values.
     */
    abstract fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, angle: Degrees = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, cornerRadius: Float = 0f, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f, uTiling: Float = 1f, vTiling: Float = 1f)

    /**
     * Draws text at a given position with a given font, size, rotation and origin.
     */
    abstract fun drawText(text: CharSequence, x: Float, y: Float, font: Font? = null, fontSize: Float = 20f, angle: Degrees = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f)

    /**
     * Draws text at a given position with a given font, size, rotation and origin.
     * Supports building the text with a [StringBuilder] to avoid allocating new [String] objects and excessive garbage.
     * Example: drawText(textBuilder = { it.append("Hello, ").append("World!") }, ..., ...)
     */
    inline fun drawText(textBuilder: (builder: StringBuilder) -> CharSequence, x: Float, y: Float, font: Font? = null, fontSize: Float = 20f, angle: Degrees = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f) =
        drawText(textBuilder(sb.clear()), x, y, font, fontSize, angle, xOrigin, yOrigin)

    /**
     * Defines a clipping area for all drawing operations performed within the [drawFunc].
     */
    inline fun drawWithin(x: Float, y: Float, width: Float, height: Float, drawFunc: () -> Unit)
    {
        applyRenderState(StencilState(x, y, width, height, action = SET))
        drawFunc()
        applyRenderState(StencilState(x, y, width, height, action = CLEAR))
    }

    ///////////////////////////////////////// Surface Config /////////////////////////////////////////

    /**
     * Sets the current color used for drawing lines, quads, text, etc.
     */
    abstract fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float = 1f): Surface

    /**
     * Sets the current color used for drawing lines, quads, text, etc.
     */
    abstract fun setDrawColor(color: Color): Surface

    /**
     * Sets the background color used when clearing the [Surface].
     */
    abstract fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float = 0f): Surface

    /**
     * Sets the background color used when clearing the [Surface].
     */
    abstract fun setBackgroundColor(color: Color): Surface

    /**
     * Sets the blending function to be used when transparent textures, quads, etc. are overlapping
     * and their color is blended.
     */
    abstract fun setBlendFunction(func: BlendFunction): Surface

    /**
     * Sets the multisampling level of the [Surface]. Prevents jagged edges and aliasing artifacts.
     * Higher values are more demanding on the GPU.
     */
    abstract fun setMultisampling(multisampling: Multisampling): Surface

    /**
     * Sets the visibility of the [Surface]. Invisible surfaces will not be drawn to the back-buffer.
     * Useful when the surface acts as off-screen render target to be used as a texture in e.g. post-processing effects.
     */
    abstract fun setIsVisible(isVisible: Boolean): Surface

    /**
     * Sets the texture format of the [Surface].
     * Set this when the surface requires higher precision than the default RGBA8 format.
     */
    abstract fun setTextureFormat(format: TextureFormat): Surface

    /**
     * Sets the texture filter used when scaling the [Surface] texture.
     */
    abstract fun setTextureFilter(filter: TextureFilter): Surface

    /**
     * Sets the scale of render target texture for the [Surface].
     * Default scale is 1.0. Higher values will increase the resolution of the surface and wise versa.
     */
    abstract fun setTextureScale(scale: Float): Surface

    ///////////////////////////////////////// Surface Textures /////////////////////////////////////////

    /**
     * Gets the texture of the off-screen render target. The render target can have multiple texture
     * attachments and the [index] can be used to get a specific one.
     */
    abstract fun getTexture(index: Int = 0): Texture

    /**
     * Gets all textures from the off-screen render target.
     */
    abstract fun getTextures(): List<Texture>

    ///////////////////////////////////////// Post-Processing /////////////////////////////////////////

    /**
     * Adds a named post-processing effect to the [Surface]. The effect will be initialized and
     * ready at the start of the next frame.
     */
    abstract fun addPostProcessingEffect(effect: PostProcessingEffect)

    /**
     * Gets a post-processing effect by name.
     */
    abstract fun getPostProcessingEffect(name: String): PostProcessingEffect?

    /**
     * Deletes a post-processing effect by name. The effect will be removed at the start of the next frame.
     */
    abstract fun deletePostProcessingEffect(name: String)

    ///////////////////////////////////////// Renderers and Render State /////////////////////////////////////////

    /**
     * Applies a render state to the [Surface]. A [RenderState] can be used to change the current OpenGL state.
     * The states will be queued up and applied while rendering to the off-screen render target.
     */
    abstract fun applyRenderState(state: RenderState)

    /**
     * Adds a [BatchRenderer] to the [Surface].
     * The renderer will be initialized and ready at the start of the next frame.
     */
    abstract fun addRenderer(renderer: BatchRenderer)

    /**
     * Deletes a [BatchRenderer] from the [Surface]. The renderer will be removed at the start of the next frame.
     */
    abstract fun getRenderers(): List<BatchRenderer>

    /**
     * Gets a [BatchRenderer] by a class type reference.
     */
    abstract fun <T: BatchRenderer> getRenderer(type: Class<T>): T?

    /**
     * Gets a [BatchRenderer] by the inferred class type.
     */
    inline fun <reified T: BatchRenderer> getRenderer(): T? = getRenderer(T::class.java)

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
    abstract fun destroy()
}