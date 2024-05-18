package testbed

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState.STOPPED
import no.njoh.pulseengine.core.PulseEngineGame
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.widgets.cli.CommandLine
import no.njoh.pulseengine.widgets.profiler.Profiler
import no.njoh.pulseengine.widgets.editor.SceneEditor

fun main() = PulseEngine.run(Testbed::class)

class Testbed : PulseEngineGame()
{
    override fun onCreate()
    {
        engine.config.gameName = "Testbed"
        engine.widget.add(SceneEditor(), CommandLine(), Profiler())
        engine.console.runScript("testbed/startup.ps")
        engine.asset.loadAllTextures("testbed/images")
        engine.scene.reload()
        engine.scene.start()
    }

    override fun onUpdate() { }

    override fun onRender()
    {
        engine.gfx.mainSurface.setDrawColor(Color.WHITE)
        engine.gfx.mainSurface.drawText(
            text = "PulseEngine 0.9.0 - Testbed",
            x = engine.window.width * 0.5f,
            y = engine.window.height * 0.5f,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
            fontSize = 72f
        )
    }

    override fun onDestroy()
    {
        if (engine.scene.state == STOPPED)
            engine.scene.save()
    }
}