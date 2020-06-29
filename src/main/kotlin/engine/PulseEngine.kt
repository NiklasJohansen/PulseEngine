package engine

import engine.widgets.ConsoleWidget
import engine.widgets.Widget
import engine.widgets.GraphWidget
import engine.data.FocusArea
import engine.data.Font
import engine.data.Sound
import engine.data.Texture
import engine.modules.*
import engine.modules.console.Console
import engine.modules.entity.EntityManager
import engine.modules.entity.EntityManagerEngineBase
import engine.modules.entity.EntityManagerBase
import engine.modules.graphics.GraphicsEngineInterface
import engine.modules.graphics.GraphicsInterface
import engine.modules.graphics.RetainedModeGraphics
import engine.util.FpsLimiter
import org.lwjgl.glfw.GLFW.glfwGetTime

// Exposed to the game code
interface GameEngine
{
    val config: ConfigurationInterface
    val window: WindowInterface
    val gfx: GraphicsInterface
    val audio: AudioInterface
    val input: InputInterface
    val network: NetworkInterface
    val asset: AssetManagerInterface
    val data: DataInterface
    val entity: EntityManagerBase
    val console: Console
}

class PulseEngine(
    override val config: ConfigurationEngineInterface = Configuration(),
    override val window: WindowEngineInterface        = Window(),
    override val gfx: GraphicsEngineInterface         = RetainedModeGraphics(),
    override val audio: AudioEngineInterface          = Audio(),
    override var input: InputEngineInterface          = Input(),
    override val network: NetworkEngineInterface      = Network(),
    override val asset: AssetManagerEngineInterface   = AssetManager(),
    override val data: MutableDataContainer           = MutableDataContainer(),
    override val entity: EntityManagerEngineBase      = EntityManager(),
    override val console: Console                     = Console(),
    private  val widgets: List<Widget>                = listOf(ConsoleWidget(), GraphWidget())
) : GameEngine {

    // Internal engine properties
    private var fpsTimer = 0.0
    private val fpsFilter = FloatArray(20)
    private var frameCounter = 0
    private var fixedUpdateAccumulator = 0.0
    private var fixedUpdateLastTime = 0.0
    private var lastFrameTime = 0.0
    private val frameRateLimiter = FpsLimiter()
    private val activeInput = input
    private val idleInput = IdleInput(activeInput)
    private val focusArea: FocusArea

    init
    {
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
            if(windowRecreated)
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

        // Sets the active input implementation
        input.setOnFocusChanged { hasFocus ->
            input = if(hasFocus) activeInput else idleInput
        }

        // Initialize engine apps
        widgets.forEach { it.init(this) }
    }

    fun run(game: Game)
    {
        // Set engine reference and initialize game
        game.engine = this
        game.init()

        // Load assets from disk
        asset.loadInitialAssets()

        // Run startup script
        console.run("run startup.ps")

        // Run main game loop
        while (window.isOpen())
        {
            update(game)
            fixedUpdate(game)
            render(game)
            updateFps()
            frameRateLimiter.sync(config.targetFps)
        }

        // Clean up game and engine
        game.cleanup()
        cleanUp()
    }

    private fun update(game: Game)
    {
        val time = glfwGetTime()
        data.deltaTime = (time - lastFrameTime).toFloat()

        updateInput()
        game.update()
        widgets.forEach { it.update(this) }

        lastFrameTime = glfwGetTime()
        data.updateTimeMS = ((glfwGetTime() - time) * 1000.0).toFloat()
        data.update()
        input = activeInput
    }

    private fun fixedUpdate(game: Game)
    {
        val dt = 1.0 / config.fixedTickRate.toDouble()
        val time = glfwGetTime()
        var frameTime = time - fixedUpdateLastTime
        if(frameTime > 0.25)
            frameTime = 0.25

        fixedUpdateLastTime = time
        fixedUpdateAccumulator += frameTime
        data.fixedDeltaTime = dt.toFloat()

        var updated = false
        while(fixedUpdateAccumulator >= dt)
        {
            audio.cleanSources()
            input.requestFocus(focusArea)
            entity.fixedUpdate(this)
            game.fixedUpdate()
            gfx.updateCamera(dt.toFloat())

            updated = true
            fixedUpdateAccumulator -= dt
            input = activeInput
        }

        if(updated)
            data.fixedUpdateTimeMS = ((glfwGetTime() - time) * 1000.0).toFloat()
    }

    private fun render(game: Game)
    {
        val startTime = glfwGetTime()
        data.interpolation = fixedUpdateAccumulator.toFloat() / data.fixedDeltaTime
        entity.render(this)
        game.render()
        widgets.forEach { it.render(this) }
        gfx.postRender()
        window.swapBuffers()
        data.renderTimeMs = ((glfwGetTime() - startTime) * 1000.0).toFloat()
    }

    private fun updateFps()
    {
        val time = glfwGetTime()
        fpsFilter[frameCounter] = 1.0f / (time - fpsTimer).toFloat()
        frameCounter = (frameCounter + 1) % fpsFilter.size
        data.currentFps = fpsFilter.average().toInt()
        fpsTimer = time
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

    private fun cleanUp()
    {
        network.cleanUp()
        audio.cleanUp()
        asset.cleanUp()
        input.cleanUp()
        gfx.cleanUp()
        window.cleanUp()
    }

    companion object
    {
        // For simple games with single draw loop
        inline fun draw(crossinline game: GameEngine.() -> Unit) = PulseEngine().run(object: Game()
        {
            override fun init() {}
            override fun update() {}
            override fun cleanup() {}
            override fun render() = game.invoke(engine)
        })

        fun run(game: Game)
        {
            PulseEngine().run(game)
        }
    }
}
