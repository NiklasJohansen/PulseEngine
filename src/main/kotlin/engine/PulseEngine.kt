package engine

import engine.apps.EngineApp
import engine.data.Font
import engine.data.Sound
import engine.data.Texture
import engine.modules.*
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
}

class PulseEngine(
    override val config: ConfigurationEngineInterface = Configuration(),
    override val window: WindowEngineInterface        = Window(),
    override val gfx: GraphicsEngineInterface         = RetainedModeGraphics(),
    override val audio: AudioEngineInterface          = Audio(),
    override val input: InputEngineInterface          = Input(),
    override val network: NetworkEngineInterface      = Network(),
    override val asset: AssetManagerEngineInterface   = AssetManager(),
    override val data: MutableDataContainer           = MutableDataContainer(),
    override val entity: EntityManagerEngineBase      = EntityManager(),
    private val apps: List<EngineApp>                 = emptyList()
) : GameEngine {

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
        window.init(config.windowWidth, config.windowHeight, config.screenMode, gfx.getRenderMode())
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

        // Notify gfx implementation about loaded textures
        asset.setOnAssetLoaded {
            when (it)
            {
                is Texture -> gfx.initTexture(it)
                is Font -> gfx.initTexture(it.charTexture)
            }
        }

        // Initialize engine apps
        apps.forEach { it.init(this) }
    }

    fun run(game: Game)
    {
        game.engine = this
        game.init()
        asset.loadInitialAssets()

        while (window.isOpen())
        {
            update(game)
            fixedUpdate(game)
            render(game)
            updateFps()
            frameRateLimiter.sync(config.targetFps)
        }

        game.cleanup()
        cleanUp()
    }

    private fun update(game: Game)
    {
        val time = glfwGetTime()
        data.deltaTime = (time - lastFrameTime).toFloat()

        updateInput()
        game.update()
        apps.forEach { it.update(this) }

        lastFrameTime = glfwGetTime()
        data.updateTimeMS = ((glfwGetTime() - time) * 1000.0).toFloat()
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
            entity.fixedUpdate(this)
            game.fixedUpdate()
            gfx.camera.updateTransform(dt.toFloat())

            fixedUpdateAccumulator -= dt
            updated = true
        }

        if(updated)
            data.fixedUpdateTimeMS = ((glfwGetTime() - time) * 1000.0).toFloat()
    }

    private fun render(game: Game)
    {
        val startTime = glfwGetTime()
        data.interpolation = fixedUpdateAccumulator.toFloat() / data.fixedDeltaTime
        gfx.preRender()
        entity.render(this)
        game.render()
        apps.forEach { it.render(this) }
        gfx.postRender(data.interpolation)
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
        val pos = gfx.camera.screenPosToWorldPos(input.xMouse, input.yMouse)
        input.xWorldMouse = pos.x
        input.yWorldMouse = pos.y
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
