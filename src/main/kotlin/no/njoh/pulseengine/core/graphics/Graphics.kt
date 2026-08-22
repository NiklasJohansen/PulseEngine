package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Shader
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.camera.CameraInternal
import no.njoh.pulseengine.core.graphics.gpu.resource.MaterialBank
import no.njoh.pulseengine.core.graphics.gpu.resource.ModelBank
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContextInternal
import no.njoh.pulseengine.core.graphics.gpu.texture.BlendFunction
import no.njoh.pulseengine.core.graphics.gpu.resource.TextureBank
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.surface.SurfaceSizeFunction
import no.njoh.pulseengine.core.graphics.surface.SurfaceOutputSpec
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.LogLevel

interface Graphics
{
    /**
     * A standard surface with default parameters intended for easy access to rendering.
     */
    val mainSurface: Surface

    /**
     * A reference to the camera associated with the main surface.
     */
    val mainCamera: Camera

    /**
     * The context holding all the 3D scene rendering state.
     */
    val sceneContext: SceneRenderContext

    /**
     * Creates and returns a new [Surface] with the given parameters.
     * The surface will be initialized and ready on the next frame.
     */
    fun createSurface(
        name: String,
        zOrder: Int? = null,
        camera: Camera? = null,
        isVisible: Boolean = true,
        clearColor: Color? = Color.BLANK,
        sizeFunction: SurfaceSizeFunction = ::defaultSurfaceSizeFunc,
        blendFunction: BlendFunction = BlendFunction.NORMAL,
        output: SurfaceOutputSpec = SurfaceOutputSpec.DEFAULT
    ): Surface

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

    companion object
    {
        /**
         * The default [SurfaceSizeFunction] uses the window size as the surface size.
         */
        fun defaultSurfaceSizeFunc(windowWidth: Int, windowHeight: Int) = PackedSize(windowWidth, windowHeight)
    }
}

interface GraphicsInternal : Graphics
{
    override val mainCamera: CameraInternal
    override val sceneContext: SceneRenderContextInternal

    val textureBank: TextureBank
    val materialBank: MaterialBank
    val modelBank: ModelBank
    val gpuName: String

    fun init(engine: PulseEngineInternal)
    fun uploadModel(model: Model)
    fun deleteModel(model: Model)
    fun uploadTexture(texture: Texture)
    fun deleteTexture(texture: Texture)
    fun uploadMaterial(material: Material)
    fun deleteMaterial(material: Material)
    fun onWindowChanged(engine: PulseEngineInternal, width: Int, height: Int, windowRecreated: Boolean)
    fun updateCameras()
    fun compileShader(shader: Shader)
    fun deleteShader(shader: Shader)
    fun initFrame(engine: PulseEngineInternal)
    fun drawFrame(engine: PulseEngineInternal)
    fun setGpuLogLevel(logLevel: LogLevel)
    fun destroy(engine: PulseEngineInternal)

    override fun getAllSurfaces(): List<SurfaceInternal>
}