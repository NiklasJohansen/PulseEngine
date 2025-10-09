package no.njoh.pulseengine.core

import no.njoh.pulseengine.core.asset.AssetManagerImpl
import no.njoh.pulseengine.core.asset.AssetManagerInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.audio.AudioImpl
import no.njoh.pulseengine.core.audio.AudioInternal
import no.njoh.pulseengine.core.config.ConfigurationImpl
import no.njoh.pulseengine.core.config.ConfigurationInternal
import no.njoh.pulseengine.core.console.ConsoleImpl
import no.njoh.pulseengine.core.console.ConsoleInternal
import no.njoh.pulseengine.core.data.DataImpl
import no.njoh.pulseengine.core.data.DataInternal
import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.input.InputImpl
import no.njoh.pulseengine.core.input.InputInternal
import no.njoh.pulseengine.core.network.NetworkImpl
import no.njoh.pulseengine.core.network.NetworkInternal
import no.njoh.pulseengine.core.scene.SceneManagerImpl
import no.njoh.pulseengine.core.scene.SceneManagerInternal
import no.njoh.pulseengine.core.shared.primitives.GameLoopMode.MULTITHREADED
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.measureMillisTime
import no.njoh.pulseengine.core.shared.utils.Extensions.toNowFormatted
import no.njoh.pulseengine.core.shared.utils.FileWatcher
import no.njoh.pulseengine.core.shared.utils.FpsLimiter
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.ThreadBarrier
import no.njoh.pulseengine.core.service.ServiceManagerImpl
import no.njoh.pulseengine.core.service.ServiceManagerInternal
import no.njoh.pulseengine.core.window.WindowImpl
import no.njoh.pulseengine.core.window.WindowInternal
import java.util.concurrent.BrokenBarrierException
import kotlin.math.min

/**
 * Main [PulseEngine] implementation.
 */
class PulseEngineImpl(
    override val config: ConfigurationInternal   = ConfigurationImpl(),
    override val window: WindowInternal          = WindowImpl(),
    override val gfx: GraphicsInternal           = GraphicsImpl(),
    override val audio: AudioInternal            = AudioImpl(),
    override var input: InputInternal            = InputImpl(),
    override val network: NetworkInternal        = NetworkImpl(),
    override val data: DataInternal              = DataImpl(),
    override val console: ConsoleInternal        = ConsoleImpl(),
    override val asset: AssetManagerInternal     = AssetManagerImpl(),
    override val scene: SceneManagerInternal     = SceneManagerImpl(),
    override val service: ServiceManagerInternal = ServiceManagerImpl()
) : PulseEngineInternal {

    @Volatile
    private var running                  = true
    private var gameThread               = null as Thread?
    private val fpsLimiter               = FpsLimiter()
    private val beginFrame               = ThreadBarrier(2)
    private val endFrame                 = ThreadBarrier(2)
    private val focusArea                = FocusArea(0f, 0f, 0f, 0f)
    private val engineStartTime          = System.nanoTime()
    private var lastFrameTimeNs          = System.nanoTime()
    private var fixedUpdateLastTimeNs    = System.nanoTime()
    private var fixedUpdateAccumulatorNs = 0L

    init { PulseEngine.INSTANCE = this }

    fun run(game: PulseEngineGame)
    {
        // Setup
        initEngine(game)
        initGame(game)
        postGameInit()
        game.onStart()

        // Run
        runGameLoop(game)

        // Destroy
        destroy(game)
    }

    private fun initEngine(game: PulseEngineGame)
    {
        printLogo()
        Logger.info { "Initializing engine (PulseEngineImpl)" }

        // Create focus area for game
        focusArea.update(0f, 0f, window.width.toFloat(), window.height.toFloat())
        input.acquireFocus(focusArea)

        // Set up window resize event handler
        window.setOnResizeEvent { w, h, windowRecreated ->
            gfx.onWindowChanged(this, w, h, windowRecreated)
            focusArea.update(0f, 0f, w.toFloat(), h.toFloat())
            if (windowRecreated)
                input.init(window.windowHandle, window.cursorPosScale)
        }

        // Let Audio module get sound assets based on name
        audio.setSoundProvider { soundAssetName -> asset.getOrNull<Sound>(soundAssetName) }

        // Reload sound buffers to new OpenAL context when output device changes
        audio.setOnOutputDeviceChanged { asset.getAllOfType<Sound>().forEachFast { audio.uploadSound(it) } }

        // Notify gfx and audio implementation about loaded textures and sounds
        asset.setOnAssetLoaded {
            when (it)
            {
                is Texture -> gfx.uploadTexture(it)
                is Font    -> gfx.uploadTexture(it.charTexture)
                is Shader  -> gfx.compileShader(it)
                is Sound   -> audio.uploadSound(it)
                is Cursor  -> input.createCursor(it)
            }
        }

        // Notify gfx and audio implementation about unloaded textures and sounds
        asset.setOnAssetUnloaded {
            when (it)
            {
                is Texture -> gfx.deleteTexture(it)
                is Font    -> gfx.deleteTexture(it.charTexture)
                is Sound   -> audio.deleteSound(it)
                is Cursor  -> input.deleteCursor(it)
            }
        }

        // Provides the save directory to the data module
        data.setOnGetSaveDirectory { config.saveDirectory }

        // Update module properties when config changes
        config.setOnChanged { propName, _ ->
            when (propName)
            {
                config::logTarget.name    -> Logger.TARGET = config.logTarget
                config::logLevel.name     -> Logger.LEVEL = config.logLevel
                config::gpuLogLevel.name  -> gfx.setGpuLogLevel(config.gpuLogLevel)
                config::gpuProfiling.name -> GpuProfiler.setEnabled(config.gpuProfiling)
            }
        }

        // Load custom cursors
        input.getCursorsToLoad().forEachFast { asset.load(it) }

        // Initialize engine components
        config.init()
        data.init()
        window.init(config)
        gfx.init(this)
        input.init(window.windowHandle, window.cursorPosScale)
        audio.init()
        network.init()
        console.init(this)
        scene.init(this, game)
    }

    private fun initGame(game: PulseEngineGame)
    {
        val startTime = System.nanoTime()
        Logger.info { "Initializing game (${game::class.simpleName})" }
        game.onCreate()
        Logger.debug { "Finished initializing game in: ${startTime.toNowFormatted()}" }
    }

    private fun postGameInit()
    {
        // Load initial assets from disk
        asset.update()

        // Initialize services
        service.init(this)

        // Remove garbage before starting game loop
        System.gc()

        // Log when finished
        Logger.info { "Started up in: ${engineStartTime.toNowFormatted()}" }
    }

    private fun runGameLoop(game: PulseEngineGame)
    {
        val isMultithreaded = (config.gameLoopMode == MULTITHREADED)

        try
        {
            if (isMultithreaded)
            {
                runInSeparateGameThread()
                {
                    while (running) runSynchronized { tick(game) }
                }
                while (running)
                {
                    beginFrame()
                    runSynchronized { drawFrame() }
                    endFrame()
                }
            }
            else while (running)
            {
                beginFrame()
                tick(game)
                drawFrame()
                endFrame()
            }
        }
        catch (e: Throwable)
        {
            Logger.error(e) { "Fatal error in game loop - shutting down" }
            Logger.writeAndOpenCrashReport()
            shutdown()
        }
    }

    private fun beginFrame()
    {
        data.update()
        asset.update()
        audio.update()
        window.initFrame(this)
        gfx.initFrame(this)
        console.update()
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
        data.gpuRenderTimeMs = measureMillisTime()
        {
            gfx.drawFrame(this)
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
        val startTime = System.nanoTime()
        data.deltaTime = ((startTime - lastFrameTimeNs).toDouble() * 1e-9).toFloat()

        game.onUpdate()
        scene.update()
        service.update(this)

        lastFrameTimeNs = System.nanoTime()
        data.cpuUpdateTimeMs = ((lastFrameTimeNs - startTime).toDouble() * 1e-6).toFloat()
        data.updateMemoryStats()
    }

    private fun fixedUpdate(game: PulseEngineGame)
    {
        val deltaTimeNs  = (1e9 / config.fixedTickRate).toLong()
        val nowNs = System.nanoTime()
        val frameTimeNs = min(nowNs - fixedUpdateLastTimeNs, 250_000_000L) // cap at 0.25s

        fixedUpdateLastTimeNs = nowNs
        fixedUpdateAccumulatorNs += frameTimeNs
        data.fixedDeltaTime = 1.0f / config.fixedTickRate

        var updated = false
        while (fixedUpdateAccumulatorNs >= deltaTimeNs)
        {
            input.requestFocus(focusArea)
            gfx.updateCameras()
            game.onFixedUpdate()
            scene.fixedUpdate()
            service.fixedUpdate(this)

            fixedUpdateAccumulatorNs -= deltaTimeNs
            updated = true
        }

        if (updated) data.cpuFixedUpdateTimeMs = ((System.nanoTime() - nowNs) / 1e6).toFloat()
    }

    private fun render(game: PulseEngineGame)
    {
        val fixedDeltaTimeNs = 1e9 / config.fixedTickRate.toDouble()
        data.interpolation = (fixedUpdateAccumulatorNs.toDouble() / fixedDeltaTimeNs).coerceIn(0.0, 1.0).toFloat()

        data.cpuRenderTimeMs = measureMillisTime()
        {
            game.onRender()
            scene.render()
            service.render(this)
        }
    }

    private fun updateInput()
    {
        input.pollEvents()

        // Update world mouse position
        val pos = gfx.mainCamera.screenPosToWorldPos(input.xMouse, input.yMouse)
        input.xWorldMouse = pos.x
        input.yWorldMouse = pos.y

        // Request base input focus for the whole window
        input.requestFocus(focusArea)
    }

    private fun destroy(game: PulseEngineGame)
    {
        game.onDestroy()
        FileWatcher.shutdown()
        gameThread?.interrupt()
        scene.destroy()
        service.destroy(this)
        audio.destroy()
        asset.destroy()
        input.destroy()
        gfx.destroy()
        window.destroy()
    }

    private fun runInSeparateGameThread(action: () -> Unit)
    {
        val runnable =
        {
            audio.enableInCurrentThread()
            try { action() }
            catch (_ : InterruptedException) { Logger.info { "Game thread interrupted - shutting down" } }
            catch (_ : BrokenBarrierException) { Logger.info { "Game thread interrupted - shutting down" } }
            catch (t: Throwable)
            {
                Logger.error(t) { "Fatal error in game thread - shutting down" }
                Logger.writeAndOpenCrashReport()
                shutdown()
            }
        }
        gameThread = Thread(runnable, "game").apply { start() }
    }

    private inline fun runSynchronized(action: () -> Unit)
    {
        beginFrame.await() // Waits for all threads to be ready
        action()           // Runs the action
        endFrame.await()   // Waits for all threads to finish
    }

    private fun shutdown()
    {
        running = false
        beginFrame.destroy()
        endFrame.destroy()
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