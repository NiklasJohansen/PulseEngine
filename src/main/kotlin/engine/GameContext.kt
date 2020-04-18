package engine

interface GameContext
{
    /**
     * Runs one time at startup
     */
    fun init(engine: EngineInterface)

    /**
     * Runs at a fixed tick rate independent of frame rate
     */
    fun fixedUpdate(engine: EngineInterface) { /* default implementation */ }

    /**
     * Runs every frame
     */
    fun update(engine: EngineInterface)

    /**
     * Runs every frame
     */
    fun render(engine: EngineInterface)

    /**
     * Runs one time before shutdown
     */
    fun cleanUp(engine: EngineInterface)
}