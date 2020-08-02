package no.njoh.pulseengine

import no.njoh.pulseengine.data.*
import no.njoh.pulseengine.data.assets.Font
import no.njoh.pulseengine.data.assets.Sound
import no.njoh.pulseengine.data.assets.Text
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.widgets.ConsoleWidget
import no.njoh.pulseengine.widgets.Widget
import no.njoh.pulseengine.widgets.GraphWidget
import no.njoh.pulseengine.modules.*
import no.njoh.pulseengine.modules.console.Console
import no.njoh.pulseengine.modules.entity.EntityManager
import no.njoh.pulseengine.modules.entity.EntityManagerEngineBase
import no.njoh.pulseengine.modules.entity.EntityManagerBase
import no.njoh.pulseengine.modules.graphics.GraphicsEngineInterface
import no.njoh.pulseengine.modules.graphics.GraphicsInterface
import no.njoh.pulseengine.modules.graphics.RetainedModeGraphics
import no.njoh.pulseengine.util.FpsLimiter
import org.lwjgl.glfw.GLFW.glfwGetTime
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

interface PulseEngine
{
    val config: ConfigurationInterface
    val window: WindowInterface
    val gfx: GraphicsInterface
    val audio: AudioInterface
    val input: InputInterface
    val network: NetworkInterface
    val asset: Assets
    val data: DataInterface
    val entity: EntityManagerBase
    val console: Console

    companion object
    {
        fun run(game: KClass<out PulseEngineGame>) =
            PulseEngineImplementation().run(game.createInstance())

        internal lateinit var GLOBAL_INSTANCE: PulseEngine
    }
}

class PulseEngineImplementation(
    override val config: ConfigurationEngineInterface = Configuration(),
    override val window: WindowEngineInterface        = Window(),
    override val gfx: GraphicsEngineInterface         = RetainedModeGraphics(),
    override val audio: AudioEngineInterface          = Audio(),
    override var input: InputEngineInterface          = Input(),
    override val network: NetworkEngineInterface      = Network(),
    override val asset: AssetsEngineInterface         = AssetsImpl(),
    override val data: MutableDataContainer           = MutableDataContainer(),
    override val entity: EntityManagerEngineBase      = EntityManager(),
    override val console: Console                     = Console(),
    private  val widgets: List<Widget>                = listOf(ConsoleWidget(), GraphWidget())
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
        network.init()
        console.init(this)

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

        // Initialize engine apps
        widgets.forEach { it.onCreate(this) }
    }

    private fun postGameCreate()
    {
        // Load assets from disk
        asset.loadInitialAssets()

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
            widgets.forEach { it.onUpdate(this) }
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
        while(data.fixedUpdateAccumulator >= dt)
        {
            audio.cleanSources()
            input.requestFocus(focusArea)
            entity.fixedUpdate(this)
            game.onFixedUpdate()
            gfx.updateCamera(dt.toFloat())

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
            entity.render(this)
            game.onRender()
            widgets.forEach { it.onRender(this) }
            gfx.postRender()
            window.swapBuffers()
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
        network.cleanUp()
        audio.cleanUp()
        asset.cleanUp()
        input.cleanUp()
        gfx.cleanUp()
        window.cleanUp()
    }
}
