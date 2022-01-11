package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.AntiAliasingType.MSAA4
import no.njoh.pulseengine.modules.graphics.renderers.FrameTextureRenderer
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.forEachFast
import no.njoh.pulseengine.util.forEachFiltered
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

class RetainedModeGraphics : GraphicsEngineInterface
{
    override lateinit var mainCamera: CameraInternal
    override lateinit var mainSurface: Surface2DInternal
    private lateinit var textureArray: TextureArray
    private lateinit var renderer: FrameTextureRenderer

    private val surfaces = mutableListOf<Surface2DInternal>()
    private var zOrder = 0

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        textureArray = TextureArray(1024, 1024, 100)
        mainCamera = DefaultCamera.createOrthographic(viewPortWidth, viewPortHeight)
        mainSurface = Surface2DImpl.create(
            name = "main",
            zOrder = zOrder--,
            initCapacity = 5000,
            textureArray = textureArray,
            camera = mainCamera,
            antiAliasing = MSAA4,
            hdrEnabled = false
        )
        surfaces.add(mainSurface)

        updateViewportSize(viewPortWidth, viewPortHeight, true)
    }

    override fun cleanUp()
    {
        Logger.info("Cleaning up graphics...")
        textureArray.cleanup()
        surfaces.forEachFast { it.cleanup() }
    }

    override fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    {
        if (windowRecreated)
        {
            GL.createCapabilities()

            // Create frameRenderer
            if (!this::renderer.isInitialized)
               renderer = FrameTextureRenderer(ShaderProgram.create(
                   vertexShaderFileName = "/pulseengine/shaders/effects/texture.vert",
                   fragmentShaderFileName = "/pulseengine/shaders/effects/texture.frag"
               ))

            // Initialize frameRenderer
            renderer.init()
        }

        // Initialize surfaces
        surfaces.forEachFast { it.init(width, height, windowRecreated) }

        // Update camera projection
        surfaces.forEachCamera { it.updateProjection(width, height) }

        // Set viewport size
        glViewport(0, 0, width, height)
    }

    override fun uploadTexture(texture: Texture)
    {
        textureArray.upload(texture)
    }

    override fun updateCameras()
    {
        surfaces.forEachCamera { it.updateLastState() }
    }

    override fun preRender()
    {
        surfaces.forEachCamera {
            it.updateViewMatrix()
            it.updateWorldPositions(mainSurface.width, mainSurface.height)
        }
    }

    override fun postRender()
    {
        surfaces.sortByDescending { it.zOrder }
        surfaces.forEachFast { it.render() }

        // Prepare OpenGL for rendering FBO textures
        glDisable(GL_DEPTH_TEST)
        glClearColor(0.043f, 0.047f, 0.054f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glViewport(0, 0, mainSurface.width, mainSurface.height)

        surfaces.forEachFiltered({ it.isVisible }) { renderer.render(it.getTexture()) }
    }

    override fun createSurface(name: String, zOrder: Int?, camera: Camera?, antiAliasing: AntiAliasingType, hdrEnabled: Boolean): Surface2D =
        surfaces
            .find { it.name == name }
            ?: Surface2DImpl.create(
                name = name,
                zOrder = zOrder ?: this.zOrder--,
                initCapacity = 5000,
                textureArray = textureArray,
                camera = (camera ?: DefaultCamera.createOrthographic(mainSurface.width, mainSurface.height)) as CameraInternal,
                antiAliasing = antiAliasing,
                hdrEnabled = hdrEnabled
            ).also {
                it.init(mainSurface.width, mainSurface.height, true)
                surfaces.add(it)
            }

    override fun getSurface(name: String): Surface2D? =
        surfaces.find { it.name == name }

    override fun getSurfaceOrDefault(name: String): Surface2D =
        surfaces.find { it.name == name } ?: run {
            Logger.error("No surface exists with name $name")
            mainSurface
        }

    override fun getAllSurfaces() = surfaces

    override fun removeSurface(name: String)
    {
        surfaces
            .find { it.name == name }
            ?.let {
                it.cleanup()
                surfaces.remove(it)
            } ?: run { Logger.error("No surface exists with name $name") }
    }

    /**
     * Iterates through each camera only once.
     */
    private inline fun List<Surface2DInternal>.forEachCamera(block: (CameraInternal) -> Unit)
    {
        val number = updateNumber++
        this.forEachFiltered({ it.camera.updateNumber != number })
        {
            block(it.camera)
            it.camera.updateNumber = number
        }
    }

    companion object
    {
        private var updateNumber = 0
    }
}