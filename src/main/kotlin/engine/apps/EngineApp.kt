package engine.apps

import engine.GameEngine

interface EngineApp
{
    fun init(engine: GameEngine)
    fun update(engine: GameEngine)
    fun render(engine: GameEngine)
    fun cleanup(engine: GameEngine)
}