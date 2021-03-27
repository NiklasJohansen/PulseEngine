package no.njoh.pulseengine.modules.scene.systems.default

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.modules.scene.Scene
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.util.forEachFast
import no.njoh.pulseengine.widgets.sceneEditor.Name

@Name("Entity Updater")
open class EntityUpdateSystem : SceneSystem()
{
    override fun onStart(scene: Scene, engine: PulseEngine)
    {
        scene.forEachEntity { it.onStart() }
    }

    override fun onFixedUpdate(scene: Scene, engine: PulseEngine)
    {
        scene.forEachEntity { it.onFixedUpdate(engine) }
    }

    override fun onUpdate(scene: Scene, engine: PulseEngine)
    {
        val sceneState = engine.scene.state
        scene.entityCollections.forEachFast { entities ->
            entities.forEachFast { entity ->
                if (sceneState == SceneState.RUNNING)
                    entity.onUpdate(engine)

                if (entity.isNot(SceneEntity.DEAD))
                    entities.keep(entity)
            }
            entities.swap()
        }
    }
}