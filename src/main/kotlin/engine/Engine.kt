package engine

import engine.modules.*
import org.lwjgl.glfw.GLFW.*
import kotlin.system.measureNanoTime

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
}

class Engine : EngineInterface {
    // Exposed engine modules
    override val window = Window()
    override val gfx = Graphics(window.width, window.height)
    override val input = Input(window.windowHandle)
    override val asset = AssetManager()
    override val audio = Audio()
    override val network = Network()

    // Exposed properties
    override var targetFps = 60
    override var currentFps = 0

    // Internal engine properties
    private var fpsTimer = 0L
    private var frameCounter = 0

    init {
        window.setOnResizeEvent { w, h -> gfx.updateViewportSize(w, h) }
    }

    fun run(gameContext: GameContext) {
        gameContext.init(this)

        while (window.isOpen()) {
            val frameTime = measureNanoTime {
                input.pollEvents()
                gameContext.update(this)
                gfx.clearBuffer()
                gameContext.render(this)
                window.swapBuffers()
            }

            updateFps(frameTime)
        }

        gameContext.cleanUp(this)
        cleanUp()
    }

    private fun updateFps(frameTimeNanoSec: Long) {
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

    private fun cleanUp() {
        audio.cleanUp()
        input.cleanUp()
        asset.cleanUp()
        window.cleanUp()
        glfwSetErrorCallback(null)
        glfwTerminate()
    }
}
