package testbed

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState.STOPPED
import no.njoh.pulseengine.core.PulseEngineGame
import no.njoh.pulseengine.modules.scene.entities.Wall
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
//        engine.scene.reload()
//        engine.scene.start()

        engine.asset.loadAllTextures("testbed/textures/brick_wall")
        engine.asset.loadAllTextures("testbed/textures/brick_wall_2")
        engine.asset.loadAllTextures("testbed/textures/stone_wall")
        engine.asset.loadAllTextures("testbed/textures")
        engine.asset.loadSpriteSheet("testbed/textures/top_down_spritesheet_2.png", "top_down_walk",5, 8)
        engine.scene.loadAndSetActive("top_down_1.scn")
        engine.scene.start()
        engine.data.addMetric("Entities", "count") { engine.scene.activeScene.entities.sumBy { it.size }.toFloat() }
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
        if (engine.scene.state == STOPPED)
            engine.scene.save()
    }
}