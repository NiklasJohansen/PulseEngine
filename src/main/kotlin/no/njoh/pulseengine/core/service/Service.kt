package no.njoh.pulseengine.core.service

import no.njoh.pulseengine.core.PulseEngine

/**
 * A service is a general-purpose process living alongside the main game.
 * The service lives on the same thread as the game and is updated and rendered in the same game loop.
 */
abstract class Service
{
    /** True if the service is currently in a running state */
    var isRunning: Boolean = false; private set

    /**
     * Called once for all added services when the engine starts
     */
    open fun onCreate(engine: PulseEngine) { }

    /**
     * Called at a fixed tick rate independent of frame rate
     */
    open fun onFixedUpdate(engine: PulseEngine) { }

    /**
     * Called once every frame
     */
    open fun onUpdate(engine: PulseEngine) { }

    /**
     * Called once every frame
     */
    open fun onRender(engine: PulseEngine) { }

    /**
     * Called once when the engine shuts down
     */
    open fun onDestroy(engine: PulseEngine) { }

    /**
     * Called when the [isRunning] flag changes.
     */
    open fun onStateChange(isRunning: Boolean) { }

    /**
     * Transitions the service to a running state
     */
    fun start()
    {
        isRunning = true
        onStateChange(true)
    }

    /**
     * Transitions the service to a stopped state
     */
    fun stop()
    {
        isRunning = false
        onStateChange(false)
    }
}