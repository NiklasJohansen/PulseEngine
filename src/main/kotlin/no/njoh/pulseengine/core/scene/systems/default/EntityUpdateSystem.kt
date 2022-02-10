package no.njoh.pulseengine.core.scene.systems.default

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.entities.SceneEntity
import no.njoh.pulseengine.core.scene.systems.SceneSystem
import no.njoh.pulseengine.widgets.editor.Name
import no.njoh.pulseengine.widgets.editor.ValueRange

@Name("Entity Updater")
open class EntityUpdateSystem : SceneSystem()
{
    @ValueRange(1f, 100000f)
    var tickRate = -1

    override fun onCreate(engine: PulseEngine)
    {
        if (tickRate == -1)
            tickRate = engine.config.fixedTickRate
    }

    override fun onStart(engine: PulseEngine)
    {
        if (tickRate != engine.config.fixedTickRate)
            engine.config.fixedTickRate = tickRate

        engine.scene.forEachEntity { it.onStart(engine) }
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (engine.scene.state == SceneState.RUNNING)
            engine.scene.forEachEntity { it.onFixedUpdate(engine) }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        val sceneState = engine.scene.state
        engine.scene.forEachEntityTypeList { typeList ->
            typeList.forEachFast { entity ->
                if (sceneState == SceneState.RUNNING)
                    entity.onUpdate(engine)

                // Handles the deletion of dead entities in the same update pass
                if (entity.isNot(SceneEntity.DEAD))
                    typeList.keep(entity)
                else
                    engine.scene.activeScene.entityIdMap.remove(entity.id)
            }
            typeList.swap()
        }
    }

    override fun handlesEntityDeletion(): Boolean = true
}