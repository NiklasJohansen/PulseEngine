package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.Multisampling.MSAA4
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA8
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.renderers.*
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*

open class GraphicsImpl : GraphicsInternal
{
    override lateinit var mainCamera: CameraInternal
    override lateinit var mainSurface: Surface2DInternal
    override lateinit var textureBank: TextureBank

    private lateinit var renderer: FrameTextureRenderer

    private val surfaces = mutableListOf<Surface2DInternal>()
    private val surfaceMap = mutableMapOf<String, Surface2DInternal>()
    private var zOrder = 0

    override fun init(viewPortWidth: Int, viewPortHeight: Int)
    {
        Logger.info("Initializing graphics (${this::class.simpleName})")

        textureBank = TextureBank()
        mainCamera = DefaultCamera.createOrthographic(viewPortWidth, viewPortHeight)
        mainSurface = createSurface(
            name = "main",
            width = viewPortWidth,
            height = viewPortHeight,
            camera = mainCamera,
            multisampling = MSAA4,
            textureFormat = RGBA8,
            textureFilter = LINEAR,
            backgroundColor = defaultClearColor.copy(),
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
                   vertexShaderFileName = "/pulseengine/shaders/default/surface.vert",
                   fragmentShaderFileName = "/pulseengine/shaders/default/surface.frag"
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
        Logger.info("Cleaning up graphics (${this::class.simpleName})")
        textureBank.cleanUp()
        renderer.cleanUp()
        surfaces.forEachFast { it.cleanUp() }
    }

    override fun uploadTexture(texture: Texture) =
        textureBank.upload(texture)

    override fun deleteTexture(texture: Texture) =
        textureBank.delete(texture)

    override fun updateCameras() =
        surfaces.forEachCamera { it.updateLastState() }

    override fun initFrame()
    {
        surfaces.forEachCamera()
        {
            it.updateViewMatrix()
            it.updateWorldPositions(mainSurface.width, mainSurface.height)
        }
        surfaces.forEachFast { it.initFrame() }
    }

    override fun drawFrame()
    {
        // Sort surfaces by Z-order
        surfaces.sortByDescending { it.context.zOrder }

        // Render all batched data to offscreen target
        surfaces.forEachFast { it.renderToOffScreenTarget() }

        // Set OpenGL state for rendering offscreen target textures
        val c = surfaces.firstOrNull()?.context?.backgroundColor ?: defaultClearColor // Clear back-buffer with color of first surface
        glClearColor(c.red, c.green, c.blue, c.alpha)
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glViewport(0, 0, mainSurface.width, mainSurface.height)

        // Run surfaces through their post-processing pipelines
        surfaces.forEachFast { it.runPostProcessingPipeline() }

        // Draw visible surfaces to back-buffer
        surfaces.forEachFiltered({ it.context.isVisible }) { renderer.render(it.getTexture()) }
    }

    override fun getAllSurfaces() = surfaces

    override fun getSurface(name: String) = surfaceMap[name]

    override fun getSurfaceOrDefault(name: String) = surfaceMap[name] ?: mainSurface

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
        multisampling: Multisampling,
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
            multisampling = multisampling,
            blendFunction = blendFunction,
            attachments = attachments,
            backgroundColor = backgroundColor
        )
        val initialCapacity = 5000
        val newSurface = Surface2DImpl(
            name = name,
            camera = newCamera,
            context = context,
            textRenderer = TextRenderer(initialCapacity, context, textureBank),
            quadRenderer = QuadBatchRenderer(initialCapacity, context),
            lineRenderer = LineBatchRenderer(initialCapacity, context),
            bindlessTextureRenderer = BindlessTextureRenderer(initialCapacity, context, textureBank),
            textureRenderer = TextureRenderer(initialCapacity, context),
            stencilRenderer = StencilRenderer(),
        )

        if (initializeSurface)
            newSurface.init(surfaceWidth, surfaceHeight, true)

        surfaces.add(newSurface)
        surfaceMap[name] = newSurface
        return newSurface
    }

    override fun setTextureCapacity(maxCount: Int, textureSize: Int, format: TextureFormat)
    {
        textureBank.setTextureCapacity(maxCount, textureSize, format)
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
        private var defaultClearColor = Color(0.043f, 0.047f, 0.054f, 0f)
    }
}