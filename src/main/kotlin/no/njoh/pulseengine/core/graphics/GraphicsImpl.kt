package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.asset.types.Shader.Companion.INVALID_ID
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.Multisampling.MSAA4
import no.njoh.pulseengine.core.graphics.api.ShaderType.*
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.renderers.*
import no.njoh.pulseengine.core.graphics.surface.*
import no.njoh.pulseengine.core.graphics.util.GpuLogger
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.PackedSize
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
    override lateinit var gpuName: String
    private  lateinit var fullFrameRenderer: FullFrameRenderer

    private val onInitFrame  = ArrayList<PulseEngineInternal.() -> Unit>()
    private val surfaceMap   = HashMap<String, SurfaceInternal>()
    private val surfaces     = ArrayList<SurfaceInternal>()
    private val errorShaders = HashMap<ShaderType, Shader>()
    private var lastZOrder   = 0

    override fun init(engine: PulseEngineInternal)
    {
        Logger.info { "Initializing graphics (GraphicsImpl)" }
        val viewPortWidth = engine.window.width
        val viewPortHeight = engine.window.height

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

        onWindowChanged(engine, viewPortWidth, viewPortHeight, windowRecreated = true)

        GpuLogger.setLogLevel(engine.config.gpuLogLevel)
    }

    override fun onWindowChanged(engine: PulseEngineInternal, width: Int, height: Int, windowRecreated: Boolean)
    {
        if (windowRecreated)
        {
            // Create OpenGL context in current thread
            GlCapabilities.create()
            gpuName = glGetString(GL_RENDERER) ?: "Unknown GPU"
            Logger.debug { "Running OpenGL on GPU: $gpuName" }

            // Load error shaders
            errorShaders[VERTEX]   = engine.asset.loadNow(VertexShader("/pulseengine/shaders/error/error.vert"))
            errorShaders[COMPUTE]  = engine.asset.loadNow(ComputeShader("/pulseengine/shaders/error/error.comp"))
            errorShaders[FRAGMENT] = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/error/error.frag"))

            // Create and initialize full frame renderer
            if (!this::fullFrameRenderer.isInitialized)
            {
                val shaderProgram = ShaderProgram.create(
                    engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/surface.vert")),
                    engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/surface.frag"))
                )
                fullFrameRenderer = FullFrameRenderer(shaderProgram)
            }
            fullFrameRenderer.init()
        }

        // Initialize surfaces
        surfaces.forEachFast { it.init(engine, width, height, windowRecreated) }

        // Update camera projection
        surfaces.forEachCamera { it.updateProjection(width, height) }

        // Set viewport size
        ViewportState.apply(mainSurface)
    }

    override fun initFrame(engine: PulseEngineInternal)
    {
        GpuProfiler.initFrame()

        onInitFrame.forEachFast { it.invoke(engine) }
        onInitFrame.clear()

        surfaces.forEachFast { it.initFrame(engine) }
        surfaces.forEachCamera()
        {
            it.updateViewMatrix()
            it.updateWorldPositions(mainSurface.config.width, mainSurface.config.height)
        }
    }

    override fun drawFrame(engine: PulseEngineInternal)
    {
        renderSurfaceContentToOffscreenTarget(engine)
        renderPostProcessingEffectsToOffscreenTarget(engine)
        renderOffscreenTargetsToBackBuffer()
        GpuProfiler.endFrame()
    }

    private fun renderSurfaceContentToOffscreenTarget(engine: PulseEngineInternal)
    {
        surfaces.forEachFiltered({ it.hasContent() })
        {
            GpuProfiler.measure(label = { "DRAW_SURFACE (" plus it.config.name plus ")" })
            {
                it.renderToOffScreenTarget(engine)
            }
        }
    }

    private fun renderPostProcessingEffectsToOffscreenTarget(engine: PulseEngineInternal)
    {
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
    }

    private fun renderOffscreenTargetsToBackBuffer()
    {
        // Set OpenGL state for rendering offscreen target textures to back-buffer
        BackBufferBaseState.apply(surfaces.firstOrNull() ?: mainSurface)

        // Draw visible surfaces with content to back-buffer
        surfaces.forEachFiltered({ it.config.isVisible && (it.hasContent() || it.hasPostProcessingEffects()) })
        {
            GpuProfiler.measure(label = { "BACK_BUFFER_DRAW (" plus it.config.name plus ")" })
            {
                fullFrameRenderer.drawTexture(it.getTexture())
            }
        }
    }

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
    ): SurfaceInternal {

        val surfaceWidth = width ?: mainSurface.config.width
        val surfaceHeight = height ?: mainSurface.config.height
        val newCamera = (camera ?: DefaultCamera.createOrthographic(surfaceWidth, surfaceHeight)) as CameraInternal
        val newSurface = SurfaceImpl(
            camera = newCamera,
            config = SurfaceConfigInternal(
                name = name,
                width = surfaceWidth,
                height = surfaceHeight,
                zOrder = zOrder ?: this.lastZOrder--,
                isVisible = isVisible,
                mipmapGenerator = mipmapGenerator,
                textureScale = textureScale,
                textureFormat = textureFormat,
                textureFilter = textureFilter,
                textureSizeFunc = textureSizeFunc,
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
                Logger.warn { "Surface with name: $name already exists. Destroying and creating new..." }
                surfaces.remove(it)
                it.destroy()
            }
            newSurface.init(this, surfaceWidth, surfaceHeight, true)
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

    override fun compileShader(shader: Shader)
    {
        val id = shader.currentId.takeIf { it != INVALID_ID } ?: glCreateShader(shader.type.value)

        Logger.debug { "Compiling shader #$id (${shader.filePath})" }
        glShaderSource(id, shader.transform(shader.sourceCode))
        glCompileShader(id)

        if (glGetShaderi(id, GL_COMPILE_STATUS) != GL_TRUE)
        {
            val info = glGetShaderInfoLog(id).removeSuffix("\n")
            Logger.error { "Failed to compile shader #$id (${shader.filePath}) \n$info" }
            shader.setId(INVALID_ID)
            shader.setErrorId(errorShaders[shader.type]!!.currentId)
            glDeleteShader(id)
        }
        else shader.setId(id)
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
        Logger.info { "Destroying graphics (${this::class.simpleName})" }
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

    private fun runOnInitFrame(command: PulseEngineInternal.() -> Unit) { onInitFrame.add(command) }
}