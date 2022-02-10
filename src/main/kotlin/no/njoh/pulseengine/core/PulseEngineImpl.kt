package no.njoh.pulseengine.core

import no.njoh.pulseengine.core.asset.AssetManagerImpl
import no.njoh.pulseengine.core.asset.AssetManagerInternal
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Sound
import no.njoh.pulseengine.core.asset.types.Text
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.audio.AudioImpl
import no.njoh.pulseengine.core.audio.AudioInternal
import no.njoh.pulseengine.core.config.ConfigurationImpl
import no.njoh.pulseengine.core.config.ConfigurationInternal
import no.njoh.pulseengine.core.console.ConsoleImpl
import no.njoh.pulseengine.core.console.ConsoleInternal
import no.njoh.pulseengine.core.data.DataImpl
import no.njoh.pulseengine.core.graphics.GraphicsImpl
import no.njoh.pulseengine.core.graphics.GraphicsInternal
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.input.InputIdle
import no.njoh.pulseengine.core.input.InputImpl
import no.njoh.pulseengine.core.input.InputInternal
import no.njoh.pulseengine.core.scene.SceneManagerImpl
import no.njoh.pulseengine.core.scene.SceneManagerInternal
import no.njoh.pulseengine.core.shared.utils.FpsLimiter
import no.njoh.pulseengine.core.widget.WidgetManagerImpl
import no.njoh.pulseengine.core.widget.WidgetManagerInternal
import no.njoh.pulseengine.core.window.WindowImpl
import no.njoh.pulseengine.core.window.WindowInternal
import org.lwjgl.glfw.GLFW

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

    private val activeInput = input
    private val idleInput = InputIdle(activeInput)
    private val frameRateLimiter = FpsLimiter()
    private lateinit var focusArea: FocusArea

    init { PulseEngine.GLOBAL_INSTANCE = this }

    fun run(game: PulseEngineGame)
    {
        // Setup engine and game
        initEngine()
        game.onCreate()
        postGameSetup()

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

    private fun initEngine()
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

    private fun postGameSetup()
    {
        // Load assets from disk
        asset.loadInitialAssets()

        // Initialize widgets
        widget.init(this)

        // Run startup script
        console.runScript("/startup.ps")
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
        val time = GLFW.glfwGetTime()
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
            gfx.updateCameras()
            scene.fixedUpdate()
            game.onFixedUpdate()

            updated = true
            data.fixedUpdateAccumulator -= dt
            input = activeInput
        }

        if (updated)
            data.fixedUpdateTimeMS = ((GLFW.glfwGetTime() - time) * 1000.0).toFloat()
    }

    private fun render(game: PulseEngineGame)
    {
        data.measureRenderTimeAndUpdateInterpolationValue()
        {
            // Get ready for rendering next frame
            gfx.initFrame()

            // Gather batch data to draw
            scene.render()
            game.onRender()
            widget.render(this)

            // Perform GPU draw calls
            gfx.drawFrame()

            // Swap front and back buffers
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