package testbed

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState.STOPPED
import no.njoh.pulseengine.modules.PulseEngineGame
import no.njoh.pulseengine.widgets.CommandLine
import no.njoh.pulseengine.widgets.Profiler
import no.njoh.pulseengine.widgets.sceneEditor.SceneEditor

fun main() = PulseEngine.run(Testbed::class)

class Testbed : PulseEngineGame()
{
    override fun onCreate()
    {
        engine.window.title = "Pulse Engine - Testbed"
        engine.config.creatorName = "PulseEngine"
        engine.config.gameName = "Testbed"
        engine.config.targetFps = 120
        engine.widget.add(CommandLine(), Profiler(), SceneEditor())
        engine.console.runScript("testbed/startup.ps")
        engine.asset.loadAllTextures("testbed/images")
        engine.scene.reload()
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
        engine.scene.saveIf { it.state == STOPPED }
    }
}