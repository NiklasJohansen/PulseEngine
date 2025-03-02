package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.config.ConfigurationInternal
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.Multisampling.MSAA4
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.renderers.*
import no.njoh.pulseengine.core.graphics.surface.*
import no.njoh.pulseengine.core.graphics.util.GpuLogger
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import no.njoh.pulseengine.core.shared.utils.LogLevel
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*

open class GraphicsImpl : GraphicsInternal
{
    override lateinit var mainCamera: CameraInternal
    override lateinit var mainSurface: SurfaceInternal
    override lateinit var textureBank: TextureBank
    private  lateinit var fullFrameRenderer: FullFrameRenderer

    private val onInitFrame = ArrayList<() -> Unit>()
    private val surfaceMap  = HashMap<String, SurfaceInternal>()
    private val surfaces    = ArrayList<SurfaceInternal>()
    private var lastZOrder  = 0

    override fun init(config: ConfigurationInternal, viewPortWidth: Int, viewPortHeight: Int)
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
            textureFormat = RGBA16F,
            textureFilter = LINEAR,
            backgroundColor = defaultClearColor.copy(),
            attachments = listOf(COLOR_TEXTURE_0, DEPTH_STENCIL_BUFFER),
        )

        updateViewportSize(viewPortWidth, viewPortHeight, true)

        GpuLogger.setLogLevel(config.getEnum("gpuLogLevel", LogLevel::class) ?: LogLevel.OFF)
    }

    override fun updateViewportSize(width: Int, height: Int, windowRecreated: Boolean)
    {
        if (windowRecreated)
        {
            // Create OpenGL context in current thread
            GL.createCapabilities()
            Logger.debug("Running OpenGL on GPU: ${glGetString(GL_RENDERER)}")

            // Create and initialize full frame renderer
            if (!this::fullFrameRenderer.isInitialized)
            {
                val vertex = "/pulseengine/shaders/default/surface.vert"
                val fragment = "/pulseengine/shaders/default/surface.frag"
                val shaderProgram = ShaderProgram.create(vertex, fragment)
                fullFrameRenderer = FullFrameRenderer(shaderProgram)
            }
            fullFrameRenderer.init()
        }

        // Initialize surfaces
        surfaces.forEachFast { it.init(width, height, windowRecreated) }

        // Update camera projection
        surfaces.forEachCamera { it.updateProjection(width, height) }

        // Set viewport size
        ViewportState.apply(mainSurface)
    }

    override fun initFrame()
    {
        GpuProfiler.initFrame()

        onInitFrame.forEachFast { it.invoke() }
        onInitFrame.clear()

        surfaces.forEachFast { it.initFrame() }
        surfaces.forEachCamera()
        {
            it.updateViewMatrix()
            it.updateWorldPositions(mainSurface.config.width, mainSurface.config.height)
        }
    }

    override fun drawFrame(engine: PulseEngine)
    {
        // Render all batched data to offscreen target
        surfaces.forEachFiltered({ it.hasContent() })
        {
            GpuProfiler.measure(label = { "DRAW_SURFACE (" plus it.config.name plus ")" })
            {
                it.renderToOffScreenTarget()
            }
        }

        // Set OpenGL state for rendering post-processing effects
        if (surfaces.anyMatches { it.hasPostProcessingEffects() })
            PostProcessingBaseState.apply(mainSurface)

        // Run surfaces through their post-processing pipelines
        surfaces.forEachFiltered({ it.hasPostProcessingEffects() })
        {
            GpuProfiler.measure(label = { "POST_PROCESS (" plus it.config.name plus ")" })
            {
                it.runPostProcessingPipeline(engine)
            }
        }

        // Set OpenGL state for rendering offscreen target textures to back-buffer
        BackBufferBaseState.apply(surfaces.firstOrNull() ?: mainSurface)

        // Draw visible surfaces with content to back-buffer
        surfaces.forEachFiltered({ it.config.isVisible && (it.hasContent() || it.hasPostProcessingEffects()) })
        {
            GpuProfiler.measure(label = { "BACK_BUFFER_DRAW (" plus it.config.name plus ")" })
            {
                fullFrameRenderer.program.bind()
                fullFrameRenderer.program.setUniformSampler("tex", it.getTexture())
                fullFrameRenderer.draw()
            }
        }

        GpuProfiler.endFrame()
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
        backgroundColor: Color
    ): SurfaceInternal {

        val surfaceWidth = width ?: mainSurface.config.width
        val surfaceHeight = height ?: mainSurface.config.height
        val newCamera = (camera ?: DefaultCamera.createOrthographic(surfaceWidth, surfaceHeight)) as CameraInternal
        val newSurface = SurfaceImpl(
            camera = newCamera,
            textureBank = textureBank,
            config = SurfaceConfigInternal(
                name = name,
                width = surfaceWidth,
                height = surfaceHeight,
                zOrder = zOrder ?: this.lastZOrder--,
                isVisible = isVisible,
                textureScale = textureScale,
                textureFormat = textureFormat,
                textureFilter = textureFilter,
                multisampling = multisampling,
                blendFunction = blendFunction,
                attachments = attachments,
                backgroundColor = backgroundColor
            )
        )

        runOnInitFrame()
        {
            surfaceMap[name]?.let()
            {
                Logger.warn("Surface with name: $name already exists. Destroying and creating new...")
                surfaces.remove(it)
                it.destroy()
            }
            newSurface.init(surfaceWidth, surfaceHeight, true)
            surfaceMap[name] = newSurface
            surfaces.add(newSurface)
            surfaces.sortBy { -it.config.zOrder }
        }

        return newSurface
    }

    override fun getAllSurfaces() = surfaces

    override fun getSurface(name: String) = surfaceMap[name]

    override fun getSurfaceOrDefault(name: String) = surfaceMap[name] ?: mainSurface

    override fun deleteSurface(name: String)
    {
        runOnInitFrame()
        {
            surfaceMap[name]?.let()
            {
                surfaces.remove(it)
                surfaceMap.remove(it.config.name)
                it.destroy()
            }
        }
    }

    override fun uploadTexture(texture: Texture) = textureBank.upload(texture)

    override fun deleteTexture(texture: Texture) = textureBank.delete(texture)

    override fun updateCameras() = surfaces.forEachCamera { it.updateLastState() }

    override fun reloadAllShaders()
    {
        runOnInitFrame()
        {
            Shader.reloadAll()
            ShaderProgram.reloadAll()
        }
    }

    override fun reloadShader(fileName: String)
    {
        runOnInitFrame()
        {
            val shader = Shader.getShaderFromAbsolutePath(fileName)
            val success = shader?.reload(fileName)
            if (success == true)
                ShaderProgram.reloadAll()
            else
                Logger.warn("Failed to reload shader: $fileName")
        }
    }

    override fun setGpuLogLevel(logLevel: LogLevel)
    {
        runOnInitFrame { GpuLogger.setLogLevel(logLevel) }
    }

    override fun setTextureCapacity(maxCount: Int, textureSize: Int, format: TextureFormat)
    {
        textureBank.setTextureCapacity(maxCount, textureSize, format)
    }

    override fun destroy()
    {
        Logger.info("Destroying graphics (${this::class.simpleName})")
        textureBank.destroy()
        fullFrameRenderer.destroy()
        surfaces.forEachFast { it.destroy() }
    }

    private inline fun List<SurfaceInternal>.forEachCamera(block: (CameraInternal) -> Unit)
    {
        // Iterates through each camera only once.
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

    private fun runOnInitFrame(command: () -> Unit) { onInitFrame.add(command) }
}