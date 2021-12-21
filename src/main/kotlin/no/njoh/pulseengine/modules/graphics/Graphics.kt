package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.AntiAliasingType.NONE

interface Graphics
{
    val mainCamera: CameraInterface
    val mainSurface: Surface2D

    fun createSurface(
        name: String,
        zOrder: Int? = null,
        camera: Camera? = null,
        antiAliasing: AntiAliasingType = NONE,
        hdrEnabled: Boolean = false
    ): Surface2D
    fun getSurface(name: String): Surface2D?
    fun getSurfaceOrDefault(name: String): Surface2D
    fun getAllSurfaces(): List<Surface2D>
    fun removeSurface(name: String)
}

interface GraphicsEngineInterface : Graphics
{
    override val mainCamera: CameraEngineInterface

    fun init(viewPortWidth: Int, viewPortHeight: Int)
    fun initTexture(texture: Texture)
    fun cleanUp()
    fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    fun updateCamera(deltaTime: Float)
    fun preRender()
    fun postRender()
}

interface LineRendererInterface
{
    fun linePoint(x0: Float, y0: Float)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float)
}