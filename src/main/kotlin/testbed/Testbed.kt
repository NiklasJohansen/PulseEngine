package testbed

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.*
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.PulseEngineGame

fun main() = PulseEngine.run(Testbed::class)

class Testbed : PulseEngineGame()
{
    override fun onCreate()
    {
        engine.window.title = "Pulse Engine - Testbed"
        engine.config.creatorName = "PulseEngine"
        engine.config.gameName = "Testbed"
        engine.config.targetFps = 120
        engine.console.runScript("/testbed/startup.ps")
    }

    override fun onUpdate()
    {

    }

    override fun onRender()
    {
        engine.gfx.mainSurface.drawText(
            text = "TESTBED",
            x = engine.window.width / 2f,
            y = engine.window.height / 2f,
            xOrigin = 0.5f,
            fontSize = 72f
        )
    }

    override fun onDestroy()
    {

    }
}