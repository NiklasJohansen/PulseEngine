package no.njoh.pulseengine.modules

import no.njoh.pulseengine.PulseEngine

abstract class PulseEngineGame
{
    /**
     * Main reference to the [PulseEngine]
     */
    val engine: PulseEngine = PulseEngine.GLOBAL_INSTANCE

    /**
     * Runs one time at startup
     */
    abstract fun onCreate()

    /**
     * Runs at a fixed tick rate independent of frame rate
     */
    open fun onFixedUpdate() { /* default implementation */ }

    /**
     * Runs every frame
     */
    abstract fun onUpdate()

    /**
     * Runs every frame
     */
    abstract fun onRender()

    /**
     * Runs one time before shutdown
     */
    abstract fun onDestroy()
}