package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Shader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Attachment
import no.njoh.pulseengine.core.graphics.api.BlendFunction
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.DefaultCamera
import no.njoh.pulseengine.core.graphics.api.DefaultCamera.ProjectionType.ORTHOGRAPHIC
import no.njoh.pulseengine.core.graphics.api.MipmapGenerator
import no.njoh.pulseengine.core.graphics.api.Multisampling
import no.njoh.pulseengine.core.graphics.api.TextureBank
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.surface.NoOpSurface
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.LogLevel

class NoOpGraphics : GraphicsInternal
{
    override var mainCamera = DefaultCamera(ORTHOGRAPHIC)
    override var mainSurface = NoOpSurface()
    override var textureBank = TextureBank()
    override val gpuName = "none"
    override fun createSurface(
        name: String,
        width: Int?,
        height: Int?,
        zOrder: Int?,
        camera: Camera?,
        isVisible: Boolean,
        mipmapGenerator: MipmapGenerator?,
        textureScale: Float,
        textureFormat: TextureFormat,
        textureFilter: TextureFilter,
        textureSizeFunc: (width: Int, height: Int, scale: Float) -> PackedSize,
        multisampling: Multisampling,
        blendFunction: BlendFunction,
        attachments: List<Attachment>,
        backgroundColor: Color
    ): Surface = mainSurface
    override fun getAllSurfaces() = emptyList<Surface>()
    override fun getSurface(name: String) = null
    override fun getSurfaceOrDefault(name: String) = mainSurface
    override fun compileShader(shader: Shader) {}
    override fun init(engine: PulseEngineInternal) {}
    override fun initFrame(engine: PulseEngineInternal) {}
    override fun onWindowChanged(engine: PulseEngineInternal, width: Int, height: Int, windowRecreated: Boolean) {}
    override fun drawFrame(engine: PulseEngineInternal) {}
    override fun deleteSurface(name: String) {}
    override fun uploadTexture(texture: Texture) {}
    override fun deleteTexture(texture: Texture) {}
    override fun updateCameras() {}
    override fun setGpuLogLevel(logLevel: LogLevel) {}
    override fun setTextureCapacity(maxCount: Int, textureSize: Int, format: TextureFormat) {}
    override fun destroy() {}
}