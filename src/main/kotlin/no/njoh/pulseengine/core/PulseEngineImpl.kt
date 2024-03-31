package no.njoh.pulseengine.core

import no.njoh.pulseengine.core.asset.AssetManagerImpl
import no.njoh.pulseengine.core.asset.AssetManagerInternal
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Sound
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.audio.AudioImpl
import no.njoh.pulseengine.core.audio.AudioInternal
import no.njoh.pulseengine.core.config.ConfigurationImpl
import no.njoh.pulseengine.core.config.ConfigurationInternal
import no.njoh.pulseengine.core.console.ConsoleImpl
import no.njoh.pulseengine.core.console.ConsoleInternal
import no.njoh.pulseengine.core.data.DataImpl
import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.input.InputIdle
import no.njoh.pulseengine.core.input.InputImpl
import no.njoh.pulseengine.core.input.InputInternal
import no.njoh.pulseengine.core.scene.SceneManagerImpl
import no.njoh.pulseengine.core.scene.SceneManagerInternal
import no.njoh.pulseengine.core.shared.primitives.GameLoopMode.MULTITHREADED
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.toNowFormatted
import no.njoh.pulseengine.core.shared.utils.FileWatcher
import no.njoh.pulseengine.core.shared.utils.FpsLimiter
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.widget.WidgetManagerImpl
import no.njoh.pulseengine.core.widget.WidgetManagerInternal
import no.njoh.pulseengine.core.window.WindowImpl
import no.njoh.pulseengine.core.window.WindowInternal
import org.lwjgl.glfw.GLFW.*
import java.util.concurrent.CyclicBarrier
import kotlin.math.min

/**
 * Main [PulseEngine] implementation.
 */
class PulseEngineImpl(
    override val config: ConfigurationInternal  = ConfigurationImpl(),
    override val window: WindowInternal         = WindowImpl(),
    override val gfx: GraphicsInternal          = GraphicsImpl(),
    override val audio: AudioInternal           = AudioImpl(),
    override var input: InputInternal           = InputImpl(),
    override val data: DataImpl                 = DataImpl(),
    override val console: ConsoleInternal       = ConsoleImpl(),
    override val asset: AssetManagerInternal    = AssetManagerImpl(),
    override val scene: SceneManagerInternal    = SceneManagerImpl(),
    override val widget: WidgetManagerInternal  = WidgetManagerImpl()
) : PulseEngine {

    private val engineStartTime = System.nanoTime()
    private val frameRateLimiter = FpsLimiter()
    private val beginFrame = CyclicBarrier(2)
    private val endFrame = CyclicBarrier(2)
    private val idleInput = InputIdle(input)
    private val activeInput = input
    private lateinit var focusArea: FocusArea

    init { PulseEngine.GLOBAL_INSTANCE = this }

    fun run(game: PulseEngineGame)
    {
        // Setup
        initEngine()
        initGame(game)
        postGameSetup()

        // Run
        runGameLoop(game)

        // Destroy
        game.onDestroy()
        destroy()
    }

    private fun initEngine()
    {
        printLogo()
        Logger.info("Initializing engine (${this::class.simpleName})")

        // Initialize engine components
        config.init()
        data.init(config.gameName)
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
            asset.getAllOfType<Sound>().forEachFast { it.reloadBuffer() }
        }

        // Notify gfx implementation about loaded textures
        asset.setOnAssetLoaded {
            when (it)
            {
                is Texture -> gfx.uploadTexture(it)
                is Font -> gfx.uploadTexture(it.charTexture)
            }
        }

        // Notify gfx implementation about deleted textures
        asset.setOnAssetRemoved {
            when (it)
            {
                is Texture -> gfx.deleteTexture(it)
                is Font -> gfx.deleteTexture(it.charTexture)
            }
        }

        // Update save directory based on creator and game name
        config.setOnChanged { property, value ->
            when (property.name)
            {
                config::gameName.name -> data.updateSaveDirectory(config.gameName)
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

    private fun initGame(game: PulseEngineGame)
    {
        val startTime = System.nanoTime()
        Logger.info("Initializing game (${game::class.simpleName})")
        game.onCreate()
        Logger.debug("Finished initializing game in: ${startTime.toNowFormatted()}")
    }

    private fun postGameSetup()
    {
        // Load assets from disk
        asset.loadInitialAssets()

        // Initialize widgets
        widget.init(this)

        // Run startup script
        console.runScript("/startup.ps")

        // Log when finished
        Logger.info("Finished initialization in ${engineStartTime.toNowFormatted()}")
    }

    private fun runGameLoop(game: PulseEngineGame)
    {
        var running = true
        val isMultithreaded = (config.gameLoopMode == MULTITHREADED)

        if (isMultithreaded)
        {
            val logicLoop = { while (running) runSyncronized { updateGameLogic(game) } }
            Thread(logicLoop, "logic").start()
        }

        while (running)
        {
            beginFrame()

            if (isMultithreaded)
            {
                // Draw previous frame simultaneously as updating next game state in logic thread
                runSyncronized { drawFrame() }
            }
            else
            {
                updateGameLogic(game)
                drawFrame()
            }

            endFrame()
            running = window.isOpen()
        }
    }

    private fun beginFrame()
    {
        data.startFrameTimer()
        gfx.initFrame()
        updateInput()
    }

    private fun updateGameLogic(game: PulseEngineGame)
    {
        update(game)
        fixedUpdate(game)
        render(game)
    }

    private fun drawFrame()
    {
        data.measureGpuRenderTime()
        {
            gfx.drawFrame()
            window.swapBuffers()
        }
    }

    private fun endFrame()
    {
        audio.cleanSources()
        frameRateLimiter.sync(config.targetFps)
        data.calculateFrameRate()
    }

    private fun update(game: PulseEngineGame)
    {
        data.updateMemoryStats()
        data.measureAndUpdateTimeStats()
        {
            console.update()
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
        val frameTime = min(time - data.fixedUpdateLastTime, 0.25)

        data.fixedUpdateLastTime = time
        data.fixedUpdateAccumulator += frameTime
        data.fixedDeltaTime = dt.toFloat()

        var updated = false
        while (data.fixedUpdateAccumulator >= dt)
        {
            input.requestFocus(focusArea)
            gfx.updateCameras()
            game.onFixedUpdate()
            scene.fixedUpdate()
            widget.fixedUpdate(this)

            updated = true
            data.fixedUpdateAccumulator -= dt
            input = activeInput
        }

        if (updated)
            data.fixedUpdateTimeMs = ((glfwGetTime() - time) * 1000.0).toFloat()
    }

    private fun render(game: PulseEngineGame)
    {
        data.updateInterpolationValue()
        data.measureCpuRenderTime()
        {
            scene.render()
            game.onRender()
            widget.render(this)
        }
    }

    private fun updateInput()
    {
        window.wasResized = false
        input.pollEvents()

        // Update world mouse position
        val pos = gfx.mainCamera.screenPosToWorldPos(input.xMouse, input.yMouse)
        input.xWorldMouse = pos.x
        input.yWorldMouse = pos.y

        // Give game area input focus
        input.requestFocus(focusArea)
    }

    private fun destroy()
    {
        // Stop logic thread if running
        Thread.getAllStackTraces().keys.find { it.name == "logic" }?.interrupt()

        FileWatcher.shutdown()
        scene.cleanUp()
        widget.cleanUp(this)
        audio.cleanUp()
        asset.cleanUp()
        activeInput.cleanUp()
        gfx.cleanUp()
        window.cleanUp()
    }

    private inline fun runSyncronized(action: () -> Unit)
    {
        beginFrame.await() // Waits for all threads to be ready
        action()           // Runs the actions
        endFrame.await()   // Waits for all threads to be finished
    }

    private fun printLogo() = println("""
        ______      _            _____            _
        | ___ \    | |          |  ___|          (_)
        | |_/ /   _| |___  ___  | |__ _ __   __ _ _ _ __   ___
        |  __/ | | | / __|/ _ \ |  __| '_ \ / _` | | '_ \ / _ \
        | |  | |_| | \__ \  __/ | |__| | | | (_| | | | | |  __/
        \_|   \__,_|_|___/\___| \____/_| |_|\__, |_|_| |_|\___|
                                             __/ |
                                            |___/   
        """.trimIndent())
}