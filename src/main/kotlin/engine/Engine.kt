package engine

import engine.modules.*
import kotlin.system.measureNanoTime

// Exposed to the game code
interface EngineInterface
{
    val window: WindowInterface
    val gfx: GraphicsInterface
    val audio: AudioInterface
    val asset: AssetManagerInterface
    val input: InputInterface
    val network: NetworkInterface

    var targetFps: Int
    val currentFps: Int
    val renderTimeMs: Float
    val updateTimeMS: Float
}

class Engine(
    override val window: WindowEngineInterface       = Window(),
    override val gfx: GraphicsEngineInterface        = SimpleGraphics(),
    override val input: InputEngineInterface         = Input(),
    override val asset: AssetManagerEngineInterface  = AssetManager(),
    override val audio: AudioEngineInterface         = Audio(),
    override val network: NetworkEngineInterface     = Network()
) : EngineInterface {

    // Exposed properties
    override var targetFps = 60
    override var currentFps = 0
    override var renderTimeMs = 0f
    override var updateTimeMS = 0f

    // Internal engine properties
    private var fpsTimer = 0L
    private var frameCounter = 0

    init
    {
        // Initialize all engine components
        window.init()
        gfx.init(window.width, window.height)
        input.init(window.windowHandle)
        asset.init()
        audio.init()
        network.init()

        // Set up event handlers
        window.setOnResizeEvent { w, h -> gfx.updateViewportSize(w, h) }
    }

    fun run(gameContext: GameContext)
    {
        gameContext.init(this)

        while (window.isOpen())
        {
            val frameTime = measureNanoTime {
                // Update step
                updateTimeMS = measureNanoTime{
                    input.pollEvents()
                    gameContext.update(this)
                } / 1000000f

                // Render step
                renderTimeMs = measureNanoTime {
                    gfx.clearBuffer()
                    gameContext.render(this)
                    gfx.render()
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
            currentFps = frameCounter
            frameCounter = 0
            fpsTimer = System.currentTimeMillis()
        }

        // TODO: Implement fixed time step
        val nanosToSleep = ((1000000000.0 / targetFps) - frameTimeNanoSec).toLong()
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
}
