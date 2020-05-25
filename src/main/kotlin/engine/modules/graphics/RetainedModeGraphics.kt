package engine.modules.graphics

import engine.data.Font
import engine.data.RenderMode
import engine.data.Texture
import engine.modules.graphics.postprocessing.PostProcessingEffect
import engine.modules.graphics.postprocessing.PostProcessingPipeline
import engine.modules.graphics.renderers.FrameTextureRenderer
import engine.modules.graphics.renderers.Renderer2D
import org.joml.Matrix4f
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

class RetainedModeGraphics : GraphicsEngineInterface
{
    override fun getRenderMode() = RenderMode.RETAINED
    override val camera: CameraEngineInterface = Camera()

    private val ppPipeline = PostProcessingPipeline()
    private val graphicState = GraphicsState()

    private val worldRenderer = Renderer2D.createDefault(100, graphicState)
    private val uiRenderer = Renderer2D.createDefault(100, graphicState)
    private var currentRenderer: Renderer2D = worldRenderer
    private val renderers = listOf(worldRenderer, uiRenderer)

    private lateinit var defaultFont: Font
    private lateinit var renderer: FrameTextureRenderer
    private val worldRenderTarget = RenderTarget()
    private val uidRenderTarget = RenderTarget()

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        updateViewportSize(viewPortWidth, viewPortHeight, true)

        // Load default font
        defaultFont = Font("/FiraSans-Regular.ttf","default_font", floatArrayOf(24f, 72f))
        defaultFont.load()
        initTexture(defaultFont.charTexture)

        camera.setOnEnableChanged { enabled ->
            currentRenderer = if(enabled) worldRenderer else uiRenderer
        }
    }

    override fun initTexture(texture: Texture)
    {
        graphicState.textureArray.upload(texture)
    }

    override fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    {
        if(windowRecreated)
            initOpenGL()

        worldRenderTarget.init(width, height)
        uidRenderTarget.init(width, height)

        glViewport(0, 0, width, height)
        graphicState.projectionMatrix = Matrix4f().ortho(0.0f, width.toFloat(), height.toFloat(), 0.0f, GraphicsState.NEAR_PLANE, GraphicsState.FAR_PLANE)
        graphicState.modelMatrix = Matrix4f()
    }

    private fun initOpenGL()
    {
        GL.createCapabilities()
        glEnable(GL_BLEND)

        renderers.forEach { it.init() }

        // Create frameRenderer
        if(!this::renderer.isInitialized)
            renderer = FrameTextureRenderer(ShaderProgram.create("/engine/shaders/effects/texture.vert", "/engine/shaders/effects/texture.frag"))

        // Initialize frameRenderer
        renderer.init()

        // Initialize post processing effects
        ppPipeline.init()

        setBackgroundColor(graphicState.bgRed, graphicState.bgGreen, graphicState.bgBlue)
        setBlendFunction(graphicState.blendFunc)
    }

    override fun cleanUp()
    {
        println("Cleaning up graphics...")
        renderers.forEach { it.cleanup() }
        graphicState.textureArray.cleanup()
        defaultFont.delete()

        worldRenderTarget.cleanUp()
        uidRenderTarget.cleanUp()
    }

    override fun preRender()
    {
        graphicState.resetDepth()
    }

    override fun postRender(interpolation: Float)
    {
        // Render world
        worldRenderTarget.begin()
        camera.enable()
        camera.updateViewMatrix(interpolation)
        worldRenderer.render(camera)
        worldRenderTarget.end()

        // Render UI
        uidRenderTarget.begin()
        camera.disable()
        camera.updateViewMatrix(interpolation)
        uiRenderer.render(camera)
        camera.enable()
        uidRenderTarget.end()

        // Prepare OpenGL for rendering FBO textures
        glDisable(GL_DEPTH_TEST)
        glClear(GL_COLOR_BUFFER_BIT)
        glEnable( GL_BLEND )
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        val worldTexture = ppPipeline.process(worldRenderTarget.getTexture())
        renderer.render(worldTexture)
        renderer.render(uidRenderTarget.getTexture())
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        currentRenderer.lineRenderer.line(x0, y0, x1, y1)
    }

    override fun drawLinePoint(x: Float, y: Float)
    {
        currentRenderer.lineRenderer.linePoint(x, y)
    }

    override fun drawSameColorLines(block: (draw: LineRendererInterface) -> Unit)
    {
        block(currentRenderer.uniColorLineRenderer)
        currentRenderer.uniColorLineRenderer.setColor(graphicState.rgba)
    }

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float)
    {
        currentRenderer.quadRenderer.quad(x, y, width, height)
    }

    override fun drawQuadVertex(x: Float, y: Float)
    {
        currentRenderer.quadRenderer.vertex(x, y)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        currentRenderer.textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)
    }

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        currentRenderer.textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin, uMin, vMin, uMax, vMax)
    }

    override fun drawText(text: String, x: Float, y: Float, font: Font?, fontSize: Float, xOrigin: Float, yOrigin: Float)
    {
        currentRenderer.textRenderer.draw(this, text, x, y, font ?: defaultFont, fontSize, xOrigin, yOrigin)
    }

    override fun setColor(red: Float, green: Float, blue: Float, alpha: Float)
    {
        graphicState.setRGBA(red, green, blue, alpha)
    }

    override fun setBackgroundColor(red: Float, green: Float, blue: Float)
    {
        glClearColor(red, green, blue, 1f)
        graphicState.bgRed = red
        graphicState.bgGreen = green
        graphicState.bgBlue = blue
    }

    override fun setBlendFunction(func: BlendFunction)
    {
        glBlendFunc(func.src, func.dest)
        graphicState.blendFunc = func
    }

    override fun setLineWidth(width: Float) { }

    override fun addPostProcessingEffect(effect: PostProcessingEffect)  =
        ppPipeline.addEffect(effect)
}

interface BatchRenderer
{
    fun init()
    fun cleanup()
    fun render(camera: CameraEngineInterface)
}
