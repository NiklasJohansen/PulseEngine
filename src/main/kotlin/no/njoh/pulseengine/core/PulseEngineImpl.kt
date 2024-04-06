package no.njoh.pulseengine.core

import no.njoh.pulseengine.core.asset.AssetManagerImpl
import no.njoh.pulseengine.core.asset.AssetManagerInternal
import no.njoh.pulseengine.core.asset.types.Cursor
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

    private val engineStartTime  = System.nanoTime()
    private val fpsLimiter       = FpsLimiter()
    private val beginFrame       = CyclicBarrier(2)
    private val endFrame         = CyclicBarrier(2)
    private val idleInput        = InputIdle(input)
    private val activeInput      = input
    private val focusArea        = FocusArea(0f, 0f, 0f, 0f)
    private var gameThread       = null as Thread?
    private var running          = true

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
        focusArea.update(0f, 0f, window.width.toFloat(), window.height.toFloat())
        input.acquireFocus(focusArea)

        // Set up window resize event handler
        window.setOnResizeEvent { w, h, windowRecreated ->
            gfx.updateViewportSize(w, h, windowRecreated)
            focusArea.update(0f, 0f, w.toFloat(), h.toFloat())
            if (windowRecreated)
                input.init(window.windowHandle)
        }

        // Reload sound buffers to new OpenAL context when output device changes
        audio.setOnOutputDeviceChanged {
            asset.getAllOfType<Sound>().forEachFast { audio.uploadSound(it) }
        }

        // Notify gfx and audio implementation about loaded textures and sounds
        asset.setOnAssetLoaded {
            when (it)
            {
                is Texture -> gfx.uploadTexture(it)
                is Font -> gfx.uploadTexture(it.charTexture)
                is Sound -> audio.uploadSound(it)
                is Cursor -> input.createCursor(it)
            }
        }

        // Notify gfx and audio implementation about deleted textures and sounds
        asset.setOnAssetRemoved {
            when (it)
            {
                is Texture -> gfx.deleteTexture(it)
                is Font -> gfx.deleteTexture(it.charTexture)
                is Sound -> audio.deleteSound(it)
                is Cursor -> input.deleteCursor(it)
            }
        }

        // Update save directory based on creator and game name
        config.setOnChanged { property, value ->
            when (property.name)
            {
                config::gameName.name -> data.updateSaveDirectory(config.gameName)
            }
        }

        // Load custom cursors
        input.getCursorsToLoad().forEachFast { asset.load(it) }

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
        // Load initial assets from disk
        asset.update()

        // Initialize widgets
        widget.init(this)

        // Run startup script
        console.runScript("/startup.ps")

        // Log when finished
        Logger.info("Finished initialization in ${engineStartTime.toNowFormatted()}")
    }

    private fun runGameLoop(game: PulseEngineGame)
    {
        val isMultithreaded = (config.gameLoopMode == MULTITHREADED)

        if (isMultithreaded)
        {
            runInSeparateGameThread()
            {
                while (running) runSyncronized { tick(game) }
            }
            while (running)
            {
                beginFrame()
                runSyncronized { drawFrame() }
                endFrame()
            }
        }
        else
        {
            while (running)
            {
                beginFrame()
                tick(game)
                drawFrame()
                endFrame()
            }
        }
    }

    private fun beginFrame()
    {
        data.startFrameTimer()
        asset.update()
        audio.update()
        gfx.initFrame()
        updateInput()
    }

    private fun tick(game: PulseEngineGame)
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
        fpsLimiter.sync(config.targetFps)
        data.calculateFrameRate()
        running = window.isOpen()
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
            game.onRender()
            scene.render()
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
        FileWatcher.shutdown()
        gameThread?.interrupt()
        scene.cleanUp()
        widget.cleanUp(this)
        audio.destroy()
        asset.cleanUp()
        activeInput.cleanUp()
        gfx.cleanUp()
        window.cleanUp()
    }

    private fun runInSeparateGameThread(action: () -> Unit)
    {
        val runnable =
        {
            audio.enableInCurrentThread()
            action()
        }
        gameThread = Thread(runnable, "game").apply { start() }
    }

    private inline fun runSyncronized(action: () -> Unit)
    {
        beginFrame.await() // Waits for all threads to be ready
        action()           // Runs the action
        endFrame.await()   // Waits for all threads to finish
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