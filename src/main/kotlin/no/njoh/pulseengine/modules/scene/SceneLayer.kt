package no.njoh.pulseengine.modules.scene

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.graphics.Surface2D

class SceneLayer(
    val name: String,
    val entities: MutableList<SceneEntity> = mutableListOf(),
    val surfaceName: String = ""
) {
    fun addEntity(entity: SceneEntity)
    {
        entities.add(entity)
    }

    fun start()
    {
        for (entity in entities)
            entity.onStart()
    }

    fun update(engine: PulseEngine)
    {
        for (entity in entities)
            entity.onUpdate(engine)
    }

    fun fixedUpdate(engine: PulseEngine)
    {
        for (entity in entities)
            entity.onFixedUpdate(engine)
    }

    fun render(surface: Surface2D, assets: Assets, isRunning: Boolean)
    {
        for (entity in entities)
            entity.onRender(surface, assets, isRunning)
    }
}