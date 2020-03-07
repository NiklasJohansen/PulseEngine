package engine

import engine.data.Sound
import engine.modules.*
import engine.modules.entity.EntityManager
import engine.modules.entity.EntityManagerEngineBase
import engine.modules.entity.EntityManagerBase
import engine.modules.rendering.GraphicsEngineInterface
import engine.modules.rendering.GraphicsInterface
import engine.modules.rendering.ImmediateModeGraphics
import org.lwjgl.glfw.GLFW.glfwGetTime

// Exposed to the game code
interface EngineInterface
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
}

class Engine(
    override val config: ConfigurationEngineInterface = Configuration(),
    override val window: WindowEngineInterface        = Window(),
    override val gfx: GraphicsEngineInterface         = ImmediateModeGraphics(),
    override val audio: AudioEngineInterface          = Audio(),
    override val input: InputEngineInterface          = Input(),
    override val network: NetworkEngineInterface      = Network(),
    override val asset: AssetManagerEngineInterface   = AssetManager(),
    override val data: DataEngineInterface            = Data(),
    override val entity: EntityManagerEngineBase      = EntityManager()
) : EngineInterface {

    // Internal engine properties
    private var fpsTimer = 0.0
    private var frameCounter = 0
    private val frameRateLimiter = FpsLimiter()

    private var fixedUpdateAccumulator = 0.0
    private var fixedUpdateLastTime = glfwGetTime()
    private var lastFrameTime = glfwGetTime()

    init
    {
        // Initialize all engine components
        config.init()
        window.init(config.windowWidth, config.windowHeight, config.screenMode)
        gfx.init(window.width, window.height)
        input.init(window.windowHandle)
        audio.init()
        network.init()
        asset.init()

        // Set up event handlers
        window.setOnResizeEvent { w, h, windowRecreated ->
            gfx.updateViewportSize(w, h, windowRecreated)
            if(windowRecreated)
                input.init(window.windowHandle)
        }

        audio.setOnOutputDeviceChanged {
            asset.getAll(Sound::class.java)
                .forEach { Sound.reloadBuffer(it) }
        }
    }

    fun run(gameContext: GameContext)
    {
        gameContext.init(this)

        while (window.isOpen())
        {
            update(gameContext)
            fixedUpdate(gameContext)
            render(gameContext)
            updateFps()
            frameRateLimiter.sync(config.targetFps)
        }

        gameContext.cleanUp(this)
        cleanUp()
    }

    private fun update(gameContext: GameContext)
    {
        data.fixedDeltaTime = (glfwGetTime() - lastFrameTime).toFloat()
        input.pollEvents()
        gameContext.update(this)
        lastFrameTime = glfwGetTime()
    }

    private fun fixedUpdate(gameContext: GameContext)
    {
        val dt = 1.0 / config.fixedTickRate.toDouble()
        data.fixedDeltaTime = dt.toFloat()

        val time = glfwGetTime()
        var frameTime = time - fixedUpdateLastTime
        if(frameTime > 0.25)
            frameTime = 0.25
        fixedUpdateLastTime = time
        fixedUpdateAccumulator += frameTime

        while(fixedUpdateAccumulator >= dt)
        {
            val startTime = glfwGetTime()
            audio.cleanSources()
            entity.fixedUpdate(this)
            gameContext.fixedUpdate(this)
            fixedUpdateAccumulator -= dt
            data.updateTimeMS = ((glfwGetTime() - startTime) * 1000.0).toFloat()
        }
    }

    private fun render(gameContext: GameContext)
    {
        val startTime = glfwGetTime()
        data.interpolation = fixedUpdateAccumulator.toFloat() / data.fixedDeltaTime
        gfx.clearBuffer()
        entity.render(this)
        gameContext.render(this)
        gfx.postRender()
        window.swapBuffers()
        data.renderTimeMs = ((glfwGetTime() - startTime) * 1000.0).toFloat()
    }

    private fun updateFps()
    {
        frameCounter++
        if (glfwGetTime() - fpsTimer >= 1.0)
        {
            data.currentFps = frameCounter
            frameCounter = 0
            fpsTimer = glfwGetTime()
        }
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
        inline fun draw(crossinline game: EngineInterface.() -> Unit) = Engine().run(object: GameContext
        {
            override fun init(engine: EngineInterface) {}
            override fun fixedUpdate(engine: EngineInterface) {}
            override fun cleanUp(engine: EngineInterface) {}
            override fun render(engine: EngineInterface) = game.invoke(engine)
        })
    }
}
