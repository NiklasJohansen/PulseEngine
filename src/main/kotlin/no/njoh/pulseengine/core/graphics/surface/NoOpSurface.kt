package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.BlendFunction
import no.njoh.pulseengine.core.graphics.api.DefaultCamera
import no.njoh.pulseengine.core.graphics.api.DefaultCamera.ProjectionType.ORTHOGRAPHIC
import no.njoh.pulseengine.core.graphics.api.Multisampling
import no.njoh.pulseengine.core.graphics.api.RenderState
import no.njoh.pulseengine.core.graphics.api.RenderTarget
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.postprocessing.PostProcessingEffect
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Degrees
import no.njoh.pulseengine.core.shared.primitives.PackedSize

class NoOpSurface: SurfaceInternal()
{
    override val camera = DefaultCamera(ORTHOGRAPHIC)
    override val config = SurfaceConfigInternal(
        name = "dummy",
        width = 0,
        height = 0,
        zOrder = 0,
        isVisible = false,
        textureScale = 1f,
        textureFormat = TextureFormat.RGBA8,
        textureFilter = TextureFilter.LINEAR,
        textureSizeFunc = { w: Int, h: Int, s: Float -> PackedSize(w * s, h * s) },
        multisampling = Multisampling.NONE,
        blendFunction = BlendFunction.NORMAL,
        attachments = emptyList(),
        backgroundColor = Color.BLANK,
        mipmapGenerator = null
    )
    override val renderTarget = RenderTarget(emptyList())
    override fun addPostProcessingEffect(effect: PostProcessingEffect) {}
    override fun addRenderer(renderer: BatchRenderer) {}
    override fun applyRenderState(state: RenderState) {}
    override fun deletePostProcessingEffect(name: String) {}
    override fun destroy() {}
    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float) {}
    override fun drawLineVertex(x: Float, y: Float) {}
    override fun drawQuad(x: Float, y: Float, width: Float, height: Float) {}
    override fun drawQuadVertex(x: Float, y: Float) {}
    override fun drawTexture(texture: RenderTexture, x: Float, y: Float, width: Float, height: Float, angle: Float, xOrigin: Float, yOrigin: Float) {}
    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, angle: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float) {}
    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, angle: Float, xOrigin: Float, yOrigin: Float, cornerRadius: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float, xTiling: Float, yTiling: Float) {}
    override fun drawText(text: CharSequence, x: Float, y: Float, font: Font?, fontSize: Float, angle: Degrees, xOrigin: Float, yOrigin: Float, wrapNewLines: Boolean, newLineSpacing: Float) {}
    override fun <T : BatchRenderer> getRenderer(type: Class<T>) = null
    override fun <T : PostProcessingEffect> getPostProcessingEffect(type: Class<T>) = null
    override fun getPostProcessingEffect(name: String) = null
    override fun getPostProcessingEffects() = emptyList<PostProcessingEffect>()
    override fun getRenderers() = emptyList<BatchRenderer>()
    override fun getTexture(index: Int, final: Boolean) = RenderTexture.BLANK
    override fun getTextures() = emptyList<RenderTexture>()
    override fun hasContent() = false
    override fun hasPostProcessingEffects() = false
    override fun init(engine: PulseEngineInternal, width: Int, height: Int, glContextRecreated: Boolean) {}
    override fun initFrame(engine: PulseEngineInternal) {}
    override fun renderToOffScreenTarget(engine: PulseEngineInternal) {}
    override fun runPostProcessingPipeline(engine: PulseEngineInternal) {}
    override fun setBackgroundColor(red: Float, green: Float, blue: Float, alpha: Float) = this
    override fun setBackgroundColor(color: Color) = this
    override fun setBlendFunction(func: BlendFunction) = this
    override fun setDrawColor(red: Float, green: Float, blue: Float, alpha: Float) = this
    override fun setDrawColor(color: Color) = this
    override fun setIsVisible(isVisible: Boolean) = this
    override fun setMultisampling(multisampling: Multisampling) = this
    override fun setTextureFilter(filter: TextureFilter) = this
    override fun setTextureFormat(format: TextureFormat) = this
    override fun setTextureScale(scale: Float) = this
}