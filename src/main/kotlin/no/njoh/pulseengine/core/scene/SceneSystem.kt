package no.njoh.pulseengine.core.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import kotlin.reflect.KClass

abstract class SceneSystem
{
    @JsonIgnore
    var initialized = false
    var enabled = true

    /**
     * Called by engine when the system is first created
     */
    fun init(engine: PulseEngine)
    {
        onCreate(engine)
        initialized = true
    }

    /**
     * Called one time when the system is created
     */
    open fun onCreate(engine: PulseEngine) { }

    /**
     * Called one time when the scene starts
     */
    open fun onStart(engine: PulseEngine) { }

    /**
     * Called at a fixed tick rate independent of frame rate
     */
    open fun onFixedUpdate(engine: PulseEngine) { }

    /**
     * Called one time every frame
     */
    open fun onUpdate(engine: PulseEngine) { }

    /**
     * Called one time every frame
     */
    open fun onRender(engine: PulseEngine) { }

    /**
     * Called one time when the scene stops
     */
    open fun onStop(engine: PulseEngine) { }

    /**
     * Called one time when the system is destroyed
     */
    open fun onDestroy(engine: PulseEngine) { }

    /**
     * Whether or not this system takes the care of deleting dead entities
     */
    open fun handlesEntityDeletion(): Boolean = false

    companion object
    {
        val REGISTERED_TYPES = mutableSetOf<KClass<out SceneSystem>>()
    }
}