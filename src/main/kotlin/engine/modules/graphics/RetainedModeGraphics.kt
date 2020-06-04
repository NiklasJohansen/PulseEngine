package engine.modules.graphics

import engine.data.Font
import engine.data.RenderMode
import engine.data.Texture
import engine.modules.graphics.postprocessing.PostProcessingEffect
import engine.modules.graphics.postprocessing.PostProcessingPipeline
import engine.modules.graphics.renderers.FrameTextureRenderer
import engine.util.forEachFiltered
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

class RetainedModeGraphics : GraphicsEngineInterface
{
    override fun getRenderMode() = RenderMode.RETAINED

    private val surfaces = mutableListOf<EngineSurface2D>()
    private val graphicState = GraphicsState()

    override lateinit var mainCamera: CameraEngineInterface
    override lateinit var mainSurface: EngineSurface2D
    private lateinit var renderer: FrameTextureRenderer

    private var zOrder = 0

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        graphicState.defaultFont = Font("/FiraSans-Regular.ttf","default_font", floatArrayOf(24f, 72f))
        graphicState.textureArray = TextureArray(1024, 1024, 100)
        graphicState.postProcessingPipeline = PostProcessingPipeline()

        mainCamera = Camera.createOrthographic(viewPortWidth, viewPortHeight)
        mainSurface = Surface2DImpl.create("main", zOrder++, 100, graphicState, mainCamera)
        mainSurface.setBackgroundColor(0.1f, 0.1f, 0.1f, 1f)
        surfaces.add(mainSurface)

        updateViewportSize(viewPortWidth, viewPortHeight, true)

        // Load default font
        graphicState.defaultFont.load()
        initTexture(graphicState.defaultFont.charTexture)
    }

    override fun cleanUp()
    {
        println("Cleaning up graphics...")
        graphicState.cleanup()
        surfaces.forEach { it.cleanup() }
    }

    override fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    {
        if(windowRecreated)
       {
           GL.createCapabilities()

           // Initialize batch renderers
           surfaces.forEach { it.initRenderers() }

           // Create frameRenderer
           if(!this::renderer.isInitialized)
               renderer = FrameTextureRenderer(ShaderProgram.create("/engine/shaders/effects/texture.vert", "/engine/shaders/effects/texture.frag"))

           // Initialize frameRenderer
           renderer.init()

           // Initialize post processing effects
           graphicState.postProcessingPipeline.init()
       }

        // Update projection of main camera
        mainCamera.updateProjection(width, height)

        // Update surfaces
        surfaces.forEach {
            it.initRenderTargets(width, height)
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
        surfaces.forEach {
            if (it.camera != mainCamera)
                it.camera.updateTransform(deltaTime)
        }
    }

    override fun postRender()
    {
        surfaces.forEach { it.render() }

        // Prepare OpenGL for rendering FBO textures
        glDisable(GL_DEPTH_TEST)
        glClear(GL_COLOR_BUFFER_BIT)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        surfaces.sortBy { it.zOrder }
        surfaces.forEach { renderer.render(it.getTexture()) }
    }

    override fun addPostProcessingEffect(effect: PostProcessingEffect)  =
        graphicState.postProcessingPipeline.addEffect(effect)

    override fun createSurface2D(name: String, zOrder: Int?, camera: CameraInterface?): Surface2D
    {
        surfaces
            .find { it.name == name }
            ?.let { return it }

        if (camera != null && camera !is CameraEngineInterface)
            throw IllegalArgumentException("Camera must implement interface [CameraEngineInterface]")

        val currentTex = mainSurface.getTexture()
        val cam = camera ?: Camera.createOrthographic(currentTex.width, currentTex.height)
        val order = zOrder ?: this.zOrder++
        val surface = Surface2DImpl.create(name, order, 100, graphicState, cam as CameraEngineInterface)

        surface.initRenderers()
        surface.initRenderTargets(currentTex.width, currentTex.height)
        surfaces.add(surface)
        return surface
    }

    override fun getSurface2D(name: String): Surface2D =
        surfaces.find { it.name == name } ?: throw RuntimeException("No surface exists with name $name")
}

interface BatchRenderer
{
    fun init()
    fun cleanup()
    fun render(camera: CameraEngineInterface)
}
