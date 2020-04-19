package engine.modules

import engine.GameEngine

abstract class Game
{
    /**
     * Main reference to the [GameEngine]
     */
    lateinit var engine: GameEngine
        internal set

    /**
     * Runs one time at startup
     */
    abstract fun init()

    /**
     * Runs at a fixed tick rate independent of frame rate
     */
    open fun fixedUpdate() { /* default implementation */ }

    /**
     * Runs every frame
     */
    abstract fun update()

    /**
     * Runs every frame
     */
    abstract fun render()

    /**
     * Runs one time before shutdown
     */
    abstract fun cleanup()
}