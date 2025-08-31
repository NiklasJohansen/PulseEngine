package no.njoh.pulseengine.core

abstract class PulseEngineGame
{
    /**
     * Main reference to the [PulseEngine]
     */
    val engine: PulseEngine = PulseEngine.INSTANCE

    /**
     * Runs one time at startup
     */
    open fun onCreate() { }

    /**
     * Runs one time at startup after game is created, assets are loaded and engine is initialized.
     */
    open fun onStart() { }

    /**
     * Runs at a fixed tick rate independent of frame rate
     */
    open fun onFixedUpdate() { }

    /**
     * Runs every frame
     */
    open fun onUpdate() { }

    /**
     * Runs every frame
     */
    open fun onRender() { }

    /**
     * Runs one time before shutdown
     */
    open fun onDestroy() { }
}