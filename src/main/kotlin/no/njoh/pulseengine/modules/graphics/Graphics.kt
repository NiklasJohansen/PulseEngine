package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.assets.Texture

interface Graphics
{
    val mainCamera: CameraInterface
    val mainSurface: Surface2D

    fun createSurface2D(name: String, zOrder: Int? = null, camera: CameraInterface? = null): Surface2D
    fun getSurface2D(name: String): Surface2D
    fun removeSurface2D(name: String)
}

interface GraphicsEngineInterface : Graphics
{
    override val mainCamera: CameraEngineInterface

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