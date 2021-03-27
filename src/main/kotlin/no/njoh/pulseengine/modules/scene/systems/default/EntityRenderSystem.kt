package no.njoh.pulseengine.modules.scene.systems.default

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.scene.Scene
import no.njoh.pulseengine.modules.scene.SurfaceName
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.widgets.sceneEditor.Name
import kotlin.reflect.full.findAnnotation

@Name("Entity Renderer")
open class EntityRenderSystem : SceneSystem()
{
    override fun onRender(scene: Scene, engine: PulseEngine)
    {
        val assets = engine.asset
        val sceneState = engine.scene.state
        val gfx = engine.gfx

        scene.entities.forEach { (_, entities) ->
            if (entities.isNotEmpty())
            {
                var surface = gfx.mainSurface
                entities[0]::class
                    .findAnnotation<SurfaceName>()
                    ?.let { surface = gfx.getSurface2D(it.name) }

                entities.forEachFast { it.onRender(surface, assets, sceneState) }
            }
        }
    }
}