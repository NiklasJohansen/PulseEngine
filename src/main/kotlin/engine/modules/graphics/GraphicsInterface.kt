package engine.modules.graphics

import engine.data.Texture
import engine.data.RenderMode
import engine.modules.graphics.postprocessing.PostProcessingEffect

// Exposed to game code
interface GraphicsInterface
{
    val mainCamera: CameraInterface
    val mainSurface: Surface2D

    fun createSurface2D(name: String, zOrder: Int? = null, camera: CameraInterface? = null): Surface2D
    fun getSurface2D(name: String): Surface2D
    fun addPostProcessingEffect(effect: PostProcessingEffect)
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
    fun updateCamera(deltaTime: Float)
    fun postRender()
}

interface LineRendererInterface
{
    fun linePoint(x0: Float, y0: Float)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float)
}