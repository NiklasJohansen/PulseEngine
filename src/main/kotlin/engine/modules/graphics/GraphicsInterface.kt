package engine.modules.graphics

import engine.data.Font
import engine.data.Texture
import engine.data.RenderMode
import engine.modules.graphics.postprocessing.PostProcessingEffect

// Exposed to game code
interface GraphicsInterface
{
    val camera: CameraInterface

    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)

    fun drawLinePoint(x: Float, y: Float)

    fun drawSameColorLines(block: (draw: LineRendererInterface) -> Unit)

    fun drawQuad(x: Float, y: Float, width: Float, height: Float)

    fun drawQuadVertex(x: Float, y: Float)

    fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f)

    fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float = 0f, xOrigin: Float = 0f, yOrigin: Float = 0f, uMin: Float = 0f, vMin: Float = 0f, uMax: Float = 1f, vMax: Float = 1f)

    fun drawText(text: String, x: Float, y: Float, font: Font? = null, fontSize: Float = -1f, xOrigin: Float = 0f, yOrigin: Float = 0f)

    fun setColor(red: Float, green: Float, blue: Float, alpha: Float = 1f)

    fun setBackgroundColor(red: Float, green: Float, blue: Float)

    fun setBlendFunction(func: BlendFunction)

    fun setLineWidth(width: Float)

    fun addPostProcessingEffect(effect: PostProcessingEffect)
}

// Exposed to game engine
interface GraphicsEngineInterface : GraphicsInterface
{
    override val camera: CameraEngineInterface

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