package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.scene.Scene
import kotlin.reflect.KClass

abstract class SceneSystem
{
    var enabled = true

    /**
     * Runs one time when the scene starts
     */
    open fun onStart(scene: Scene, engine: PulseEngine) { }

    /**
     * Runs at a fixed tick rate independent of frame rate
     */
    open fun onFixedUpdate(scene: Scene, engine: PulseEngine) { }

    /**
     * Runs one time every frame
     */
    open fun onUpdate(scene: Scene, engine: PulseEngine) { }

    /**
     * Runs one time every frame
     */
    open fun onRender(scene: Scene, engine: PulseEngine) { }

    /**
     * Runs one time when the scene stops
     */
    open fun onStop(scene: Scene, engine: PulseEngine) { }

    companion object
    {
        val REGISTERED_TYPES = mutableSetOf<KClass<out SceneSystem>>()
    }
}