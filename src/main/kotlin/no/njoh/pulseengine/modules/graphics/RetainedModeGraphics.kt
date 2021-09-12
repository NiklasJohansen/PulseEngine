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
    private val surfaces = mutableListOf<EngineSurface2D>()
    private val graphicState = GraphicsState()

    override lateinit var mainCamera: CameraEngineInterface
    override lateinit var mainSurface: EngineSurface2D
    private lateinit var renderer: FrameTextureRenderer

    private var zOrder = 0

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        graphicState.textureArray = TextureArray(1024, 1024, 100)

        mainCamera = Camera.createOrthographic(viewPortWidth, viewPortHeight)
        mainSurface = Surface2DImpl.create(
            name = "main",
            zOrder = zOrder--,
            initCapacity = 5000,
            graphicsState = graphicState,
            camera = mainCamera,
            antiAliasing = MSAA4
        )
        surfaces.add(mainSurface)

        updateViewportSize(viewPortWidth, viewPortHeight, true)
    }

    override fun cleanUp()
    {
        Logger.info("Cleaning up graphics...")
        graphicState.cleanup()
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

        // Update projection of main camera
        mainCamera.updateProjection(width, height)

        // Initialize surfaces
        surfaces.forEachFast {
            it.init(width, height, windowRecreated)
            if (it.camera != mainCamera)
                it.camera.updateProjection(width, height)
        }

        // Set viewport size
        glViewport(0, 0, width, height)
    }

    override fun initTexture(texture: Texture)
    {
        graphicState.textureArray.upload(texture)
    }

    override fun updateCamera(deltaTime: Float)
    {
        mainCamera.updateTransform(deltaTime)
        surfaces.forEachFast {
            if (it.camera != mainCamera)
                it.camera.updateTransform(deltaTime)
        }
    }

    override fun postRender()
    {
        surfaces.forEachFast { it.render() }

        // Prepare OpenGL for rendering FBO textures
        glDisable(GL_DEPTH_TEST)
        glClearColor(0.043f, 0.047f, 0.054f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        surfaces.sortByDescending { it.zOrder }
        surfaces.forEachFiltered({ it.isVisible }) { renderer.render(it.getTexture()) }
    }

    override fun createSurface(name: String, zOrder: Int?, camera: CameraInterface?, antiAliasing: AntiAliasingType): Surface2D =
        surfaces
            .find { it.name == name }
            ?: Surface2DImpl.create(
                name = name,
                zOrder = zOrder ?: this.zOrder--,
                initCapacity = 5000,
                graphicsState = graphicState,
                camera = (camera ?: Camera.createOrthographic(mainSurface.width, mainSurface.height)) as CameraEngineInterface,
                antiAliasing = antiAliasing
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

    override fun getSurface(name: String): Surface2D? =
        surfaces.find { it.name == name }

    override fun removeSurface(name: String)
    {
        surfaces
            .find { it.name == name }
            ?.let {
                it.cleanup()
                surfaces.remove(it)
            } ?: run { Logger.error("No surface exists with name $name") }
    }
}

interface BatchRenderer
{
    fun init()
    fun cleanup()
    fun render(camera: CameraEngineInterface)
}
