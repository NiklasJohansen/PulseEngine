package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.api.Multisampling.NONE
import no.njoh.pulseengine.core.graphics.api.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.api.Attachment.DEPTH_STENCIL_BUFFER
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA8
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.CameraInternal
import no.njoh.pulseengine.core.graphics.surface.Surface

interface Graphics
{
    val mainCamera: Camera
    val mainSurface: Surface

    fun getSurface(name: String): Surface?
    fun getSurfaceOrDefault(name: String): Surface
    fun getAllSurfaces(): List<Surface>
    fun deleteSurface(name: String)
    fun createSurface(
        name: String,
        width: Int? = null,
        height: Int? = null,
        zOrder: Int? = null,
        camera: Camera? = null,
        isVisible: Boolean = true,
        textureScale: Float = 1f,
        textureFormat: TextureFormat = RGBA8,
        textureFilter: TextureFilter = LINEAR,
        multisampling: Multisampling = NONE,
        blendFunction: BlendFunction = BlendFunction.NORMAL,
        attachments: List<Attachment> = listOf(COLOR_TEXTURE_0, DEPTH_STENCIL_BUFFER),
        backgroundColor: Color = Color(0.1f, 0.1f, 0.1f, 0f),
    ) : Surface

    fun setTextureCapacity(maxCount: Int, textureSize: Int, format: TextureFormat = RGBA8)
}

interface GraphicsInternal : Graphics
{
    override val mainCamera: CameraInternal
    val textureBank: TextureBank

    fun init(viewPortWidth: Int, viewPortHeight: Int)
    fun uploadTexture(texture: Texture)
    fun deleteTexture(texture: Texture)
    fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    fun updateCameras()
    fun initFrame()
    fun drawFrame()
    fun destroy()
}