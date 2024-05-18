package no.njoh.pulseengine.core.widget

import no.njoh.pulseengine.core.PulseEngine

interface Widget
{
    /** True if the widget is currently in a running state */
    var isRunning: Boolean

    /**
     * Called once for all added widgets when the engine starts
     */
    fun onCreate(engine: PulseEngine) { }

    /**
     * Called at a fixed tick rate independent of frame rate
     */
    fun onFixedUpdate(engine: PulseEngine) { }

    /**
     * Called once every frame
     */
    fun onUpdate(engine: PulseEngine) { }

    /**
     * Called once every frame
     */
    fun onRender(engine: PulseEngine) { }

    /**
     * Called once when the engine shuts down
     */
    fun onDestroy(engine: PulseEngine) { }

    /**
     * Transitions the widget to a running state
     */
    fun start(): Widget { isRunning = true; return this }

    /**
     * Transitions the widget to a stopped state
     */
    fun stop(): Widget { isRunning = false; return this }
}