package no.njoh.pulseengine.core.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.ScnProp

@Name("Entity Updater")
open class EntityUpdater : SceneSystem()
{
    @ScnProp(min = 1f, max = 100000f)
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
                {
                    // Keep this entity for the next frame
                    typeList.keep(entity)
                }
                else
                {
                    engine.scene.activeScene.entityIdMap[entity.parentId]?.removeChild(entity)
                    engine.scene.activeScene.entityIdMap.remove(entity.id)
                }
            }
            typeList.swap()
        }
    }

    override fun handlesEntityDeletion(): Boolean = true
}