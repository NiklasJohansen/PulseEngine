package engine.modules.graphics

import engine.data.Font
import engine.data.RenderMode
import engine.data.Texture
import engine.modules.graphics.postprocessing.PostProcessingEffect
import engine.modules.graphics.postprocessing.PostProcessingPipeline
import engine.modules.graphics.renderers.FrameTextureRenderer
import engine.modules.graphics.renderers.GraphicsLayer
import engine.modules.graphics.renderers.LayerType
import org.joml.Matrix4f
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

class RetainedModeGraphics : GraphicsEngineInterface
{
    override fun getRenderMode() = RenderMode.RETAINED
    override val camera: CameraEngineInterface = Camera()

    private val graphicState = GraphicsState()
    private var currentLayer = GraphicsLayer.create("default", LayerType.WORLD, 100, graphicState)
    private val graphicsLayers = mutableListOf(currentLayer)
    private val ppPipeline = PostProcessingPipeline()

    override lateinit var mainCamera: CameraEngineInterface
    override lateinit var mainSurface: EngineSurface2D
    private lateinit var renderer: FrameTextureRenderer

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        updateViewportSize(viewPortWidth, viewPortHeight, true)

        // Load default font
        defaultFont = Font("/FiraSans-Regular.ttf","default_font", floatArrayOf(24f, 72f))
        defaultFont.load()
        initTexture(defaultFont.charTexture)
    }

    override fun initTexture(texture: Texture)
    {
        graphicState.textureArray.upload(texture)
    }

    override fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    {
        if(windowRecreated)
            initOpenGL()

        graphicsLayers.forEach { it.initRenderTargets(width, height) }

        glViewport(0, 0, width, height)
        graphicState.projectionMatrix = Matrix4f().ortho(0.0f, width.toFloat(), height.toFloat(), 0.0f, GraphicsState.NEAR_PLANE, GraphicsState.FAR_PLANE)
        graphicState.modelMatrix = Matrix4f()
    }

    private fun initOpenGL()
    {
        GL.createCapabilities()

        // Debugging
        // GLUtil.setupDebugMessageCallback();

        // Initialize batch renderers
        graphicsLayers.forEach { it.initRenderers() }

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
        graphicState.textureArray.cleanup()
        defaultFont.delete()
        graphicsLayers.forEach { it.cleanup() }
    }

    override fun preRender()
    {
        graphicState.resetDepth()
        useLayer("default")
    }

    override fun postRender()
    {
        surfaces.forEach { it.render() }

        // Prepare OpenGL for rendering FBO textures
        glDisable(GL_DEPTH_TEST)
        glClear(GL_COLOR_BUFFER_BIT)
        glEnable( GL_BLEND )
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        graphicsLayers.forEach {
            if(it.layerType == LayerType.WORLD)
                renderer.render(ppPipeline.process(it.renderTarget.getTexture()))
        }

        graphicsLayers
            .forEach {
                if (it.layerType == LayerType.UI)
                    renderer.render(it.renderTarget.getTexture())
            }

        graphicsLayers
            .forEach {
                if (it.layerType == LayerType.UI)
                    renderer.render(it.renderTarget.getTexture())
            }

        graphicsLayers
            .forEach {
                if (it.layerType == LayerType.OVERLAY)
                    renderer.render(it.renderTarget.getTexture())
            }
    }

    override fun drawSameColorLines(block: (draw: LineRendererInterface) -> Unit)
    {
        block(currentLayer.uniColorLineRenderer)
        currentLayer.uniColorLineRenderer.setColor(graphicState.rgba)
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float) =
        currentLayer.lineRenderer.line(x0, y0, x1, y1)

    override fun drawLinePoint(x: Float, y: Float) =
        currentLayer.lineRenderer.linePoint(x, y)

    override fun drawQuad(x: Float, y: Float, width: Float, height: Float) =
        currentLayer.quadRenderer.quad(x, y, width, height)

    override fun drawQuadVertex(x: Float, y: Float) =
        currentLayer.quadRenderer.vertex(x, y)

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float) =
        currentLayer.textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin)

    override fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float) =
        currentLayer.textureRenderer.drawTexture(texture, x, y, width, height, rot, xOrigin, yOrigin, uMin, vMin, uMax, vMax)

    override fun drawText(text: String, x: Float, y: Float, font: Font?, fontSize: Float, xOrigin: Float, yOrigin: Float) =
        currentLayer.textRenderer.draw(this, text, x, y, font ?: defaultFont, fontSize, xOrigin, yOrigin)

    override fun setColor(red: Float, green: Float, blue: Float, alpha: Float) =
        graphicState.setRGBA(red, green, blue, alpha)

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

    override fun addLayer(name: String, type: LayerType)
    {
        if (graphicsLayers.none { it.name == name })
        {
            val layer = GraphicsLayer.create(name, type, 100, graphicState)
            val currentTex = currentLayer.renderTarget.getTexture()
            layer.initRenderers()
            layer.initRenderTargets(currentTex.width, currentTex.height)
            graphicsLayers.add(layer)
        }
    }

    override fun useLayer(name: String)
    {
        currentLayer = graphicsLayers.find { it.name == name }
            ?: throw RuntimeException("No graphics layer exists with name $name")
    }

}

interface BatchRenderer
{
    fun init()
    fun cleanup()
    fun render(camera: CameraEngineInterface)
}
