package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Shader
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
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR_MIPMAP
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.LogLevel
import kotlin.math.max

interface Graphics
{
    /**
     * A standard surface set up with default parameters intended for easy access to rendering.
     */
    val mainSurface: Surface

    /**
     * A reference to camera associated with the main surface.
     */
    val mainCamera: Camera

    /**
     * Returns the [Surface] with the given name or null if it does not exist.
     */
    fun getSurface(name: String): Surface?

    /**
     * Returns the [Surface] with the given name if it exists or the default [mainSurface].
     */
    fun getSurfaceOrDefault(name: String): Surface

    /**
     * Returns all available [Surface]s.
     */
    fun getAllSurfaces(): List<Surface>

    /**
     * Deletes the [Surface] with the given name.
     */
    fun deleteSurface(name: String)

    /**
     * Creates and returns a new [Surface] with the given parameters.
     * The surface will be initialized and ready on the next frame.
     */
    fun createSurface(
        name: String,
        width: Int? = null,
        height: Int? = null,
        zOrder: Int? = null,
        camera: Camera? = null,
        isVisible: Boolean = true,
        mipmapGenerator: MipmapGenerator? = null,
        textureScale: Float = 1f,
        textureFormat: TextureFormat = RGBA16F,
        textureFilter: TextureFilter = if (mipmapGenerator != null) LINEAR_MIPMAP else LINEAR,
        textureSizeFunc: (width: Int, height: Int, scale: Float) -> PackedSize = ::defaultTexSizeFunc,
        multisampling: Multisampling = NONE,
        blendFunction: BlendFunction = BlendFunction.NORMAL,
        attachments: List<Attachment> = listOf(COLOR_TEXTURE_0, DEPTH_STENCIL_BUFFER),
        backgroundColor: Color = Color.BLANK
    ) : Surface

    /**
     * Sets the number of textures that should be allocated up-front on the GPU when a
     * texture asset matching the given size and format is loaded.
     */
    fun setTextureCapacity(maxCount: Int, textureSize: Int, format: TextureFormat = RGBA8)

    companion object
    {
        /**
         * Default size function that scales the texture width and height by the given scale factor,
         * ensuring that the minimum size is 1x1.
         */
        fun defaultTexSizeFunc(width: Int, height: Int, scale: Float) =
            PackedSize(max(width * scale, 1f), max(height * scale, 1f))
    }
}

interface GraphicsInternal : Graphics
{
    override val mainCamera: CameraInternal
    val textureBank: TextureBank
    val gpuName: String

    fun init(engine: PulseEngineInternal)
    fun uploadTexture(texture: Texture)
    fun deleteTexture(texture: Texture)
    fun onWindowChanged(engine: PulseEngineInternal, width: Int, height: Int, windowRecreated: Boolean)
    fun updateCameras()
    fun compileShader(shader: Shader)
    fun initFrame(engine: PulseEngineInternal)
    fun drawFrame(engine: PulseEngineInternal)
    fun setGpuLogLevel(logLevel: LogLevel)
    fun destroy()
}