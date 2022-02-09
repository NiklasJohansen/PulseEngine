package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.api.*
import no.njoh.pulseengine.modules.graphics.api.AntiAliasing.NONE
import no.njoh.pulseengine.modules.graphics.api.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.modules.graphics.api.Attachment.DEPTH_STENCIL_BUFFER
import no.njoh.pulseengine.modules.graphics.api.TextureFilter.BILINEAR_INTERPOLATION
import no.njoh.pulseengine.modules.graphics.api.TextureFormat.NORMAL

interface Graphics
{
    val mainCamera: Camera
    val mainSurface: Surface2D

    fun getSurface(name: String): Surface2D?
    fun getSurfaceOrDefault(name: String): Surface2D
    fun getAllSurfaces(): List<Surface2D>
    fun deleteSurface(name: String)
    fun createSurface(
        name: String,
        width: Int? = null,
        height: Int? = null,
        zOrder: Int? = null,
        camera: Camera? = null,
        isVisible: Boolean = true,
        textureScale: Float = 1f,
        textureFormat: TextureFormat = NORMAL,
        textureFilter: TextureFilter = BILINEAR_INTERPOLATION,
        antiAliasing: AntiAliasing = NONE,
        blendFunction: BlendFunction = BlendFunction.NORMAL,
        attachments: List<Attachment> = listOf(COLOR_TEXTURE_0, DEPTH_STENCIL_BUFFER),
        backgroundColor: Color = Color(0.1f, 0.1f, 0.1f, 0f),
        initializeSurface: Boolean = true
    ) : Surface2D
}

interface GraphicsInternal : Graphics
{
    override val mainCamera: CameraInternal

    fun init(viewPortWidth: Int, viewPortHeight: Int)
    fun uploadTexture(texture: Texture)
    fun deleteTexture(texture: Texture)
    fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    fun updateCameras()
    fun initFrame()
    fun drawFrame()
    fun cleanUp()
}