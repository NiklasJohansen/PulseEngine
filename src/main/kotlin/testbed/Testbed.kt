package testbed

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState.STOPPED
import no.njoh.pulseengine.core.PulseEngineGame
import no.njoh.pulseengine.widgets.cli.CommandLine
import no.njoh.pulseengine.widgets.profiler.Profiler
import no.njoh.pulseengine.widgets.editor.SceneEditor

fun main() = PulseEngine.run(Testbed::class)

class Testbed : PulseEngineGame()
{
    override fun onCreate()
    {
        engine.window.title = "Pulse Engine - Testbed"
        engine.config.gameName = "Testbed"
        engine.config.targetFps = 120
        engine.widget.add(SceneEditor(), CommandLine(), Profiler())
        engine.console.runScript("testbed/startup.ps")
        engine.asset.loadAllTextures("testbed/images")
        engine.scene.reload()
        engine.scene.start()
    }

    override fun onUpdate()
    {

    }

    override fun onRender()
    {
        engine.gfx.mainSurface.drawText(
            text = "PulseEngine 0.6.0 - Testbed",
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