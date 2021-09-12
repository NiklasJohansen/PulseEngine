package no.njoh.pulseengine

import no.njoh.pulseengine.data.*
import no.njoh.pulseengine.data.assets.*
import no.njoh.pulseengine.modules.*
import no.njoh.pulseengine.modules.console.ConsoleImpl
import no.njoh.pulseengine.modules.console.Console
import no.njoh.pulseengine.modules.graphics.GraphicsEngineInterface
import no.njoh.pulseengine.modules.graphics.Graphics
import no.njoh.pulseengine.modules.graphics.RetainedModeGraphics
import no.njoh.pulseengine.modules.scene.SceneManager
import no.njoh.pulseengine.modules.scene.SceneManagerEngineInterface
import no.njoh.pulseengine.modules.scene.SceneManagerImpl
import no.njoh.pulseengine.modules.widget.*
import no.njoh.pulseengine.util.FpsLimiter
import org.lwjgl.glfw.GLFW.glfwGetTime
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

interface PulseEngine
{
    val config: Configuration
    val window: Window
    val gfx: Graphics
    val audio: Audio
    val input: Input
    val asset: Assets
    val scene: SceneManager
    val data: Data
    val console: Console
    val widget: WidgetManager

    companion object
    {
        fun run(game: KClass<out PulseEngineGame>) =
            PulseEngineImpl().run(game.createInstance())

        internal lateinit var GLOBAL_INSTANCE: PulseEngine
    }
}

class PulseEngineImpl(
    override val config: ConfigurationEngineInterface = ConfigurationImpl(),
    override val window: WindowEngineInterface        = WindowImpl(),
    override val gfx: GraphicsEngineInterface         = RetainedModeGraphics(),
    override val audio: AudioEngineInterface          = AudioImpl(),
    override var input: InputEngineInterface          = InputImpl(),
    override val asset: AssetsEngineInterface         = AssetsImpl(),
    override val scene: SceneManagerEngineInterface   = SceneManagerImpl(),
    override val data: MutableDataContainer           = MutableDataContainer(),
    override val console: ConsoleImpl                 = ConsoleImpl(),
    override val widget: WidgetManagerEngineInterface = WidgetManagerImpl()
) : PulseEngine {

    private val activeInput = input
    private val idleInput = IdleInput(activeInput)
    private val frameRateLimiter = FpsLimiter()
    private lateinit var focusArea: FocusArea

    init { PulseEngine.GLOBAL_INSTANCE = this }

    private fun preGameCreate()
    {
        printLogo()

        // Initialize engine components
        config.init()
        data.init(config.creatorName, config.gameName)
        window.init(config.windowWidth, config.windowHeight, config.screenMode, config.gameName)
        gfx.init(window.width, window.height)
        input.init(window.windowHandle)
        audio.init()
        console.init(this)
        scene.init(this)

        // Create focus area for game
        focusArea = FocusArea(0f, 0f, window.width.toFloat(), window.height.toFloat())
        input.acquireFocus(focusArea)

        // Set up window resize event handler
        window.setOnResizeEvent { w, h, windowRecreated ->
            gfx.updateViewportSize(w, h, windowRecreated)
            focusArea.update(0f, 0f, w.toFloat(), h.toFloat())
            if (windowRecreated)
                input.init(window.windowHandle)
        }

        // Reload sound buffers to new OpenAL context
        audio.setOnOutputDeviceChanged {
            asset.getAll(Sound::class.java).forEach { it.reloadBuffer() }
        }

        // Notify gfx implementation about loaded textures
        asset.setOnAssetLoaded {
            when (it)
            {
                is Texture -> gfx.initTexture(it)
                is Font -> gfx.initTexture(it.charTexture)
            }
        }

        // Update save directory based on creator and game name
        config.setOnChanged { property, value ->
            when (property.name)
            {
                config::creatorName.name -> data.updateSaveDirectory(config.creatorName, config.gameName)
                config::gameName.name -> data.updateSaveDirectory(config.creatorName, config.gameName)
            }
        }

        // Load all custom cursors
        input.loadCursors { fileName, assetName, xHotspot, yHotspot ->
            asset.loadCursor(fileName, assetName, xHotspot, yHotspot)
        }

        // Sets the active input implementation
        input.setOnFocusChanged { hasFocus ->
            input = if (hasFocus) activeInput else idleInput
        }
    }

    private fun postGameCreate()
    {
        // Load assets from disk
        asset.loadInitialAssets()

        // Initialize widgets
        widget.init(this)

        // Run startup script
        console.runScript("/startup.ps")
    }

    fun run(game: PulseEngineGame)
    {
        // Setup engine and game
        preGameCreate()
        game.onCreate()
        postGameCreate()

        // Run main game loop
        while (window.isOpen())
        {
            update(game)
            fixedUpdate(game)
            render(game)
            syncFps()
        }

        // Clean up game and engine
        game.onDestroy()
        destroy()
    }

    private fun update(game: PulseEngineGame)
    {
        data.updateMemoryStats()
        data.measureAndUpdateTimeStats()
        {
            updateInput()
            game.onUpdate()
            scene.update()
            widget.update(this)
            input = activeInput
        }
    }

    private fun fixedUpdate(game: PulseEngineGame)
    {
        val dt = 1.0 / config.fixedTickRate.toDouble()
        val time = glfwGetTime()
        var frameTime = time - data.fixedUpdateLastTime
        if (frameTime > 0.25)
            frameTime = 0.25

        data.fixedUpdateLastTime = time
        data.fixedUpdateAccumulator += frameTime
        data.fixedDeltaTime = dt.toFloat()

        var updated = false
        while (data.fixedUpdateAccumulator >= dt)
        {
            audio.cleanSources()
            input.requestFocus(focusArea)
            gfx.updateCamera(dt.toFloat())
            scene.fixedUpdate()
            game.onFixedUpdate()

            updated = true
            data.fixedUpdateAccumulator -= dt
            input = activeInput
        }

        if (updated)
            data.fixedUpdateTimeMS = ((glfwGetTime() - time) * 1000.0).toFloat()
    }

    private fun render(game: PulseEngineGame)
    {
        data.measureRenderTimeAndUpdateInterpolationValue()
        {
            gfx.preRender()
            scene.render()
            game.onRender()
            widget.render(this)
            gfx.postRender()
            window.swapBuffers()
            window.wasResized = false
        }
    }

    private fun syncFps()
    {
        data.calculateFrameRate()
        frameRateLimiter.sync(config.targetFps)
    }

    private fun updateInput()
    {
        input.pollEvents()

        // Update world mouse position
        val pos = gfx.mainCamera.screenPosToWorldPos(input.xMouse, input.yMouse)
        input.xWorldMouse = pos.x
        input.yWorldMouse = pos.y

        // Give game area input focus
        input.requestFocus(focusArea)
    }

    private fun printLogo() =
        Text("/pulseengine/assets/logo.txt", "logo")
            .let {
                it.load()
                println("${it.text}\n")
            }

    private fun destroy()
    {
        scene.cleanUp()
        widget.cleanUp(this)
        audio.cleanUp()
        asset.cleanUp()
        input.cleanUp()
        gfx.cleanUp()
        window.cleanUp()
    }
}
