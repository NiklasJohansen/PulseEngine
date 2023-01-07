package no.njoh.pulseengine.core.scene.interfaces

import no.njoh.pulseengine.core.PulseEngine

/**
 * Gives the entity the ability to be initiated.
 */
interface Initiable
{
    /**
     * Called once when the entity is created.
     */
    fun onCreate() { }

    /**
     * Called once when the scene starts.
     */
    fun onStart(engine: PulseEngine) {  }
}