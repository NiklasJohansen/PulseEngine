package testbed

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState.STOPPED
import no.njoh.pulseengine.core.PulseEngineGame
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.widgets.cli.CommandLine
import no.njoh.pulseengine.widgets.metrics.MetricViewer
import no.njoh.pulseengine.widgets.editor.SceneEditor
import no.njoh.pulseengine.widgets.metrics.GpuMonitor

fun main() = PulseEngine.run(Testbed::class)

class Testbed : PulseEngineGame()
{
    override fun onCreate()
    {
        engine.config.gameName = "Testbed 0.10.0"
        engine.widget.add(SceneEditor(), CommandLine(), MetricViewer(), GpuMonitor())
        engine.console.runScript("testbed/startup.ps")
        engine.asset.loadAll("testbed/images")
        engine.scene.reload() // Load default.scn from disk
        engine.scene.start()
    }

    override fun onUpdate() { }

    override fun onRender()
    {
        engine.gfx.mainSurface.setDrawColor(Color.WHITE)
        engine.gfx.mainSurface.drawText(
            text = "PulseEngine 0.10.0 - Testbed",
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
            engine.scene.save() // Save default.scn to disk
    }
}