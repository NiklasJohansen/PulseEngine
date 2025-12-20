package no.njoh.pulseengine.core.service

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal

/**
 * A service is a general-purpose process living alongside the main game.
 * The service lives on the same thread as the game and is updated and rendered in the same game loop.
 */
abstract class Service
{
    /** True if the service is currently in a running state */
    var isRunning: Boolean = false; private set

    /**
     * Called from the engine thread once when the engine starts.
     */
    open fun onCreate(engine: PulseEngine) { }

    /**
     * Called from the game thread at a fixed tick rate independent of frame rate.
     * Use this for physics and other time-critical updates.
     */
    open fun onFixedUpdate(engine: PulseEngine) { }

    /**
     * Called from the game thread once every frame.
     * Use this for general updates, input handling, etc.
     */
    open fun onUpdate(engine: PulseEngine) { }

    /**
     * Called from the game thread once every frame.
     * Use this for submitting everything that needs to be rendered next frame.
     */
    open fun onRender(engine: PulseEngine) { }

    /**
     * Called from the engine thread once when the engine shuts down
     * to allow the service to clean up resources.
     */
    open fun onDestroy(engine: PulseEngine) { }

    /**
     * Called when the [isRunning] flag changes.
     * Use this to start/stop any internal processes.
     */
    open fun onStateChange(isRunning: Boolean) { }

    /**
     * Transitions the service to a running state.
     */
    fun start()
    {
        isRunning = true
        onStateChange(true)
    }

    /**
     * Transitions the service to a stopped state.
     */
    fun stop()
    {
        isRunning = false
        onStateChange(false)
    }
}

/**
 * Internal extension of [Service] with additional lifecycle methods called from the engine thread.
 */
abstract class ServiceInternal : Service()
{
    /**
     * Called from the engine thread once before each frame.
     * This is a synchronization point where only engine thread operations are performed.
     * The game thread waits for this method to complete before proceeding.
     */
    open fun onFrameStart(engine: PulseEngine) { }

    /**
     * Called from the engine thread once before the frame is drawn.
     */
    open fun onFrameDraw(engine: PulseEngineInternal) {}

    /**
     * Called from the engine thread once after each frame.
     * This is a synchronization point where only engine thread operations are performed.
     * The game thread waits for this method to complete before proceeding.
     */
    open fun onFrameEnd(engine: PulseEngine) { }
}