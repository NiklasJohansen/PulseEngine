package engine.widgets

import engine.GameEngine

interface Widget
{
    fun init(engine: GameEngine)
    fun update(engine: GameEngine)
    fun render(engine: GameEngine)
    fun cleanup(engine: GameEngine)
}