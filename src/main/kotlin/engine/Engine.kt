package engine

import engine.modules.*
import engine.modules.entity.EntityManager
import engine.modules.entity.EntityManagerEngineBase
import engine.modules.entity.EntityManagerBase
import engine.modules.rendering.GraphicsEngineInterface
import engine.modules.rendering.GraphicsInterface
import engine.modules.rendering.ImmediateModeGraphics
import kotlin.system.measureNanoTime

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
    private var fpsTimer = 0L
    private var frameCounter = 0

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
    }

    fun run(gameContext: GameContext)
    {
        gameContext.init(this)

        var lastTime = System.currentTimeMillis() / 1000.0
        var timeAccumulator = 0.0

        while (window.isOpen())
        {
            val dt = 1.0 / config.tickRate.toDouble()
            val time = System.currentTimeMillis() / 1000.0
            var frameTime = time - lastTime
            if(frameTime > 0.25)
                frameTime = 0.25
            lastTime = time

            timeAccumulator += frameTime

            while(timeAccumulator >= dt)
            {
                update(gameContext, dt)
                timeAccumulator -= dt
            }

            val interpolation = timeAccumulator / dt

            render(gameContext, interpolation)
            updateFps()
        }

        gameContext.cleanUp(this)
        cleanUp()
    }

    private fun update(gameContext: GameContext, deltaTime: Double)
    {
        data.deltaTime = deltaTime.toFloat()
        val updateTime = measureNanoTime{
            input.pollEvents()
            entity.update(this)
            gameContext.update(this)
        }

        data.updateTimeMS = updateTime / 1000000f
    }

    private fun render(gameContext: GameContext, frameInterpolation: Double)
    {
        data.interpolation = frameInterpolation.toFloat()
        val renderTime = measureNanoTime {
            gfx.clearBuffer()
            entity.render(this)
            gameContext.render(this)
            gfx.postRender()
            window.swapBuffers()
        }

        data.renderTimeMs = renderTime / 1000000f
    }

    private fun updateFps()
    {
        frameCounter++
        if (System.currentTimeMillis() - fpsTimer >= 1000) {
            data.currentFps = frameCounter
            frameCounter = 0
            fpsTimer = System.currentTimeMillis()
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
            override fun update(engine: EngineInterface) {}
            override fun cleanUp(engine: EngineInterface) {}
            override fun render(engine: EngineInterface) = game.invoke(engine)
        })
    }
}
