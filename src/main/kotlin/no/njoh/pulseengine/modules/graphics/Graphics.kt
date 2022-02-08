package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.AntiAliasingType.NONE
import no.njoh.pulseengine.modules.graphics.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.modules.graphics.Attachment.DEPTH_STENCIL_BUFFER
import no.njoh.pulseengine.modules.graphics.TextureFilter.BILINEAR_INTERPOLATION
import no.njoh.pulseengine.modules.graphics.TextureFormat.NORMAL

interface Graphics
{
    val mainCamera: Camera
    val mainSurface: Surface2D

    fun createSurface(
        name: String,
        zOrder: Int? = null,
        camera: Camera? = null,
        textureFormat: TextureFormat = NORMAL,
        textureFilter: TextureFilter = BILINEAR_INTERPOLATION,
        antiAliasing: AntiAliasingType = NONE,
        attachments: List<Attachment> = listOf(COLOR_TEXTURE_0, DEPTH_STENCIL_BUFFER)
    ): Surface2D
    fun getSurface(name: String): Surface2D?
    fun getSurfaceOrDefault(name: String): Surface2D
    fun getAllSurfaces(): List<Surface2D>
    fun deleteSurface(name: String)
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