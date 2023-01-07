package no.njoh.pulseengine.core.scene.interfaces

import no.njoh.pulseengine.core.PulseEngine

/**
 * Gives the entity the ability to be updated.
 */
interface Updatable
{
    /**
     * Called each frame.
     */
    fun onUpdate(engine: PulseEngine)

    /**
     * Called at a fixed tick rate independent of frame rate
     */
    fun onFixedUpdate(engine: PulseEngine)
}