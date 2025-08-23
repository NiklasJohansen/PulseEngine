package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Updatable

@Name("Entity Updater")
open class EntityUpdater : SceneSystem()
{
    @Prop(min = 1f, max = 100000f)
    var tickRate = -1f

    override fun onCreate(engine: PulseEngine)
    {
        if (tickRate == -1f)
            tickRate = engine.config.fixedTickRate
    }

    override fun onStart(engine: PulseEngine)
    {
        if (tickRate != engine.config.fixedTickRate)
            engine.config.fixedTickRate = tickRate

        engine.scene.forEachEntityOfType<Initiable> { it.onStart(engine) }
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (engine.scene.state == SceneState.RUNNING)
            engine.scene.forEachEntityOfType<Updatable> { it.onFixedUpdate(engine) }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (engine.scene.state == SceneState.RUNNING)
            engine.scene.forEachEntityOfType<Updatable> { it.onUpdate(engine) }
    }
}