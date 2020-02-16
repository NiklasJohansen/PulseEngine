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
        window.init(config.windowWidth, config.windowHeight)
        gfx.init(config.windowWidth, config.windowHeight)
        input.init(window.windowHandle)
        audio.init()
        network.init()
        asset.init()

        // Set up event handlers
        window.setOnResizeEvent { w, h -> gfx.updateViewportSize(w, h) }
    }

    fun run(gameContext: GameContext)
    {
        gameContext.init(this)

        while (window.isOpen())
        {
            val frameTime = measureNanoTime {
                gfx.clearBuffer()

                // Update step
                data.updateTimeMS = measureNanoTime{
                    input.pollEvents()
                    gameContext.update(this)
                } / 1000000f

                // Render step
                data.renderTimeMs = measureNanoTime {
                    entity.update(this)
                    gameContext.render(this)
                    gfx.postRender()
                    window.swapBuffers()
                } / 1000000f
            }

            updateFps(frameTime)
        }

        gameContext.cleanUp(this)
        cleanUp()
    }

    private fun updateFps(frameTimeNanoSec: Long)
    {
        frameCounter++
        if (System.currentTimeMillis() - fpsTimer >= 1000) {
            data.currentFps = frameCounter
            frameCounter = 0
            fpsTimer = System.currentTimeMillis()
        }

        // TODO: Implement fixed time step
        val nanosToSleep = ((1000000000.0 / config.targetFps) - frameTimeNanoSec).toLong()
        if (nanosToSleep > 0)
            Thread.sleep(nanosToSleep / 1000000, (nanosToSleep % 1000000).toInt())
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
