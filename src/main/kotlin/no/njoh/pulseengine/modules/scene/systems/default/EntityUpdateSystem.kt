package no.njoh.pulseengine.modules.scene.systems.default

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.modules.scene.Scene
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.util.forEachFast
import no.njoh.pulseengine.widgets.sceneEditor.Name
import no.njoh.pulseengine.widgets.sceneEditor.ValueRange

@Name("Entity Updater")
open class EntityUpdateSystem : SceneSystem()
{
    @ValueRange(1f, 100000f)
    var tickRate = -1

    override fun onCreate(scene: Scene, engine: PulseEngine)
    {
        if (tickRate == -1)
            tickRate = engine.config.fixedTickRate
    }

    override fun onStart(scene: Scene, engine: PulseEngine)
    {
        if (tickRate != engine.config.fixedTickRate)
            engine.config.fixedTickRate = tickRate

        scene.forEachEntity { it.onStart(engine) }
    }

    override fun onFixedUpdate(scene: Scene, engine: PulseEngine)
    {
        if (engine.scene.state == SceneState.RUNNING)
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
                else
                    scene.killEntity(entity)
            }
            entities.swap()
        }
    }
}