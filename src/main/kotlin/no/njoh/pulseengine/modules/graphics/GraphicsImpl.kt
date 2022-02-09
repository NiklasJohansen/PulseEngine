package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.api.AntiAliasing.MSAA4
import no.njoh.pulseengine.modules.graphics.api.Attachment.*
import no.njoh.pulseengine.modules.graphics.api.TextureFilter.BILINEAR_INTERPOLATION
import no.njoh.pulseengine.modules.graphics.api.TextureFormat.NORMAL
import no.njoh.pulseengine.modules.graphics.api.*
import no.njoh.pulseengine.modules.graphics.renderers.*
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.forEachFast
import no.njoh.pulseengine.util.forEachFiltered
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

class GraphicsImpl : GraphicsInternal
{
    override lateinit var mainCamera: CameraInternal
    override lateinit var mainSurface: Surface2DInternal
    private lateinit var textureArray: TextureArray
    private lateinit var renderer: FrameTextureRenderer

    private val surfaces = mutableListOf<Surface2DInternal>()
    private val surfaceMap = mutableMapOf<String, Surface2DInternal>()
    private var zOrder = 0

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        textureArray = TextureArray(1024, 1024, 100)
        mainCamera = DefaultCamera.createOrthographic(viewPortWidth, viewPortHeight)
        mainSurface = createSurface(
            name = "main",
            width = viewPortWidth,
            height = viewPortHeight,
            camera = mainCamera,
            antiAliasing = MSAA4,
            textureFormat = NORMAL,
            textureFilter = BILINEAR_INTERPOLATION,
            attachments = listOf(COLOR_TEXTURE_0, DEPTH_STENCIL_BUFFER),
            initializeSurface = false // Will be initialized in next step
        )

        updateViewportSize(viewPortWidth, viewPortHeight, true)
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

    override fun cleanUp()
    {
        Logger.info("Cleaning up graphics...")
        textureArray.cleanUp()
        renderer.cleanUp()
        surfaces.forEachFast { it.cleanUp() }
    }

    override fun uploadTexture(texture: Texture) =
        textureArray.upload(texture)

    override fun deleteTexture(texture: Texture) =
        textureArray.delete(texture)

    override fun updateCameras()
    {
        surfaces.forEachCamera { it.updateLastState() }
    }

    override fun initFrame()
    {
        surfaces.forEachCamera()
        {
            it.updateViewMatrix()
            it.updateWorldPositions(mainSurface.width, mainSurface.height)
        }
    }

    override fun drawFrame()
    {
        // Sort surfaces by Z-order
        surfaces.sortByDescending { it.context.zOrder }

        // Render all batched data to offscreen target
        surfaces.forEachFast { it.renderToOffScreenTarget() }

        // Set OpenGL state for rendering offscreen target textures
        glDisable(GL_DEPTH_TEST)
        glClearColor(0.043f, 0.047f, 0.054f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glViewport(0, 0, mainSurface.width, mainSurface.height)

        // Run surfaces through their post processing pipelines
        surfaces.forEachFast { it.runPostProcessingPipeline() }

        // Draw visible surfaces to back-buffer
        surfaces.forEachFiltered({ it.context.isVisible }) { renderer.render(it.getTexture()) }
    }

    override fun getAllSurfaces() = surfaces

    override fun getSurface(name: String): Surface2D? = surfaceMap[name]

    override fun getSurfaceOrDefault(name: String): Surface2D =
        surfaceMap[name] ?: run {
            Logger.debug("No surface exists with name $name")
            mainSurface
        }

    override fun deleteSurface(name: String)
    {
        surfaceMap[name]?.let {
            it.cleanUp()
            surfaces.remove(it)
            surfaceMap.remove(it.name)
        }
    }

    override fun createSurface(
        name: String,
        width: Int?,
        height: Int?,
        zOrder: Int?,
        camera: Camera?,
        isVisible: Boolean,
        textureScale: Float,
        textureFormat: TextureFormat,
        textureFilter: TextureFilter,
        antiAliasing: AntiAliasing,
        blendFunction: BlendFunction,
        attachments: List<Attachment>,
        backgroundColor: Color,
        initializeSurface: Boolean
    ): Surface2DInternal {
        // Return existing surface if available
        surfaceMap[name]?.let { return it }

        // Create new surface
        val surfaceWidth = width ?: mainSurface.width
        val surfaceHeight = height ?: mainSurface.height
        val newCamera = (camera ?: DefaultCamera.createOrthographic(surfaceWidth, surfaceHeight)) as CameraInternal
        val context = RenderContextInternal(
            zOrder = zOrder ?: this.zOrder--,
            isVisible = isVisible,
            textureScale = textureScale,
            textureFormat = textureFormat,
            textureFilter = textureFilter,
            antiAliasing = antiAliasing,
            blendFunction = blendFunction,
            attachments = attachments,
            backgroundColor = backgroundColor
        )
        val initialCapacity = 5000
        val newSurface = Surface2DImpl(
            name = name,
            camera = newCamera,
            context = context,
            textRenderer = TextRenderer(),
            quadRenderer = QuadBatchRenderer(initialCapacity, context),
            lineRenderer = LineBatchRenderer(initialCapacity, context),
            bindlessTextureRenderer = BindlessTextureRenderer(initialCapacity, context, textureArray),
            textureRenderer = TextureRenderer(initialCapacity, context)
        )

        if (initializeSurface)
            newSurface.init(surfaceWidth, surfaceHeight, true)

        surfaces.add(newSurface)
        surfaceMap[name] = newSurface
        return newSurface
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