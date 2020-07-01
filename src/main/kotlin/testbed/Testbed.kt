package testbed

import engine.PulseEngine
import engine.modules.PulseEngineGame

fun main() = PulseEngine.run(Testbed::class)

class Testbed : PulseEngineGame()
{
    override fun onCreate()
    {
        engine.window.title = "Testbed"
        engine.config.targetFps = 120
    }

    override fun onUpdate()
    {

    }

    override fun onRender()
    {
        engine.gfx.mainSurface.drawText("TESTBED", engine.window.width / 2f, engine.window.height / 2f, xOrigin = 0.5f, fontSize = 72f)
    }

    override fun onDestroy()
    {

    }
}