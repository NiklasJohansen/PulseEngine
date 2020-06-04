package engine.modules.graphics

import engine.data.Font
import engine.data.Texture
import engine.data.RenderMode
import engine.modules.graphics.postprocessing.PostProcessingEffect
import engine.modules.graphics.renderers.LayerType

// Exposed to game code
interface GraphicsInterface
{
    val mainCamera: CameraInterface
    val mainSurface: Surface2D

    fun setBlendFunction(func: BlendFunction)

    fun setLineWidth(width: Float)

    fun addPostProcessingEffect(effect: PostProcessingEffect)

    fun addLayer(name: String, type: LayerType)

    fun useLayer(name: String)
}

// Exposed to game engine
interface GraphicsEngineInterface : GraphicsInterface
{
    override val mainCamera: CameraEngineInterface

    fun getRenderMode(): RenderMode
    fun init(viewPortWidth: Int, viewPortHeight: Int)
    fun initTexture(texture: Texture)
    fun cleanUp()
    fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    fun preRender()
    fun postRender()
}

interface LineRendererInterface
{
    fun linePoint(x0: Float, y0: Float)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float)
}