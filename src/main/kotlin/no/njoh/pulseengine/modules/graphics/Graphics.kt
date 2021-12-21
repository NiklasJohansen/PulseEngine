package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.AntiAliasingType.NONE

interface Graphics
{
    val mainCamera: Camera
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
    override val mainCamera: CameraInternal

    fun init(viewPortWidth: Int, viewPortHeight: Int)
    fun uploadTexture(texture: Texture)
    fun cleanUp()
    fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    fun updateCameras()
    fun preRender()
    fun postRender()
}