package engine

import engine.abstraction.GameContext
import engine.data.Font
import engine.data.Sound
import engine.data.Texture
import engine.modules.*
import engine.modules.entity.EntityManager
import engine.modules.entity.EntityManagerEngineBase
import engine.modules.entity.EntityManagerBase
import engine.modules.graphics.GraphicsEngineInterface
import engine.modules.graphics.GraphicsInterface
import engine.modules.graphics.ImmediateModeGraphics
import org.lwjgl.glfw.GLFW.glfwGetTime
import java.lang.IllegalArgumentException

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
) : EngineInterface() {

    // Internal engine properties
    private var fpsTimer = 0.0
    private val fpsFilter = FloatArray(20)
    private var frameCounter = 0
    private var fixedUpdateAccumulator = 0.0
    private var fixedUpdateLastTime = glfwGetTime()
    private var lastFrameTime = glfwGetTime()
    private val frameRateLimiter = FpsLimiter()

    init
    {
        // Initialize engine components
        config.init()
        window.init(config.windowWidth, config.windowHeight, config.screenMode)
        gfx.init(window.width, window.height)
        input.init(window.windowHandle)
        audio.init()
        network.init()

        // Set up window resize event handler
        window.setOnResizeEvent { w, h, windowRecreated ->
            gfx.updateViewportSize(w, h, windowRecreated)
            if(windowRecreated)
                input.init(window.windowHandle)
        }

        // Reload sound buffers to new OpenAL context
        audio.setOnOutputDeviceChanged {
            asset.getAll(Sound::class.java).forEach { it.reloadBuffer() }
        }

        // Notify gfx implementation about loaded assets
        asset.setOnAssetLoaded {
            when (it)
            {
                is Texture -> gfx.initTexture(it)
                is Font -> gfx.initTexture(it.charTexture)
            }
        }
    }

    fun run(gameContext: GameContext)
    {
        gameContext.init(this)
        asset.loadInitialAssets()

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
        val time = glfwGetTime()
        data.deltaTime = (time - lastFrameTime).toFloat()

        input.pollEvents()
        gameContext.update(this)

        lastFrameTime = glfwGetTime()
        data.updateTimeMS = ((glfwGetTime() - time) * 1000.0).toFloat()
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

        var updated = false
        while(fixedUpdateAccumulator >= dt)
        {
            audio.cleanSources()
            entity.fixedUpdate(this)
            gameContext.fixedUpdate(this)

            fixedUpdateAccumulator -= dt
            updated = true
        }

        if(updated)
            data.fixedUpdateTimeMS = ((glfwGetTime() - time) * 1000.0).toFloat()
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
        val time = glfwGetTime()
        fpsFilter[frameCounter] = 1.0f / (time - fpsTimer).toFloat()
        frameCounter = (frameCounter + 1) % fpsFilter.size
        data.currentFps = fpsFilter.average().toInt()
        fpsTimer = time
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
            override fun update(engine: EngineInterface) {}
            override fun cleanUp(engine: EngineInterface) {}
            override fun render(engine: EngineInterface) = game.invoke(engine)
        })
    }
}
