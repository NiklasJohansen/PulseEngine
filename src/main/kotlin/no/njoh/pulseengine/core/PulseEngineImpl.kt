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
    private lateinit var gameThread: Thread

    init { PulseEngine.GLOBAL_INSTANCE = this }

    fun run(game: PulseEngineGame)
    {
        // Setup engine and game
        initEngine()
        initGame(game)
        postGameSetup()

        // Start loops
        startGameLoop(game)
        startMainLoop()

        // Clean up game and engine
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
        Logger.info("Initializing game (${game::class.simpleName})")
        val startTime = System.nanoTime()
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

    private fun startGameLoop(game: PulseEngineGame)
    {
        gameThread = Thread()
        {
            while (true)
            {
                beginFrame.await()
                update(game)
                fixedUpdate(game)
                render(game)
                endFrame.await()
            }
        }
        gameThread.name = "game"
        gameThread.start()
    }

    private fun startMainLoop()
    {
        while (window.isOpen())
        {
            data.measureTotalFrameTime()
            {
                // Set up frame
                gfx.initFrame()
                audio.cleanSources()
                updateInput()

                // Wait for every thread to be ready
                beginFrame.await()

                // Let GPU draw previous frame
                data.measureGpuRenderTime()
                {
                    gfx.drawFrame()
                    window.swapBuffers()
                }

                // Wait for every thread to finish
                endFrame.await()
                syncFps()
            }
        }
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
            data.fixedUpdateTimeMS = ((glfwGetTime() - time) * 1000.0).toFloat()
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

    private fun syncFps()
    {
        data.calculateFrameRate()
        frameRateLimiter.sync(config.targetFps)
    }

    private fun destroy()
    {
        gameThread.stop()
        FileWatcher.shutdown()
        scene.cleanUp()
        widget.cleanUp(this)
        audio.cleanUp()
        asset.cleanUp()
        activeInput.cleanUp()
        gfx.cleanUp()
        window.cleanUp()
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