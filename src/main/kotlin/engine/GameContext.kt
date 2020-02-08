package engine

interface GameContext
{
    fun init(engine: EngineInterface)
    fun update(engine: EngineInterface)
    fun render(engine: EngineInterface)
    fun cleanUp(engine: EngineInterface)
}