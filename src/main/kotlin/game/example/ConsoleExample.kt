package game.example

import engine.PulseEngine
import engine.modules.console.CommandResult
import engine.modules.Game
import engine.modules.console.ConsoleTarget

fun main() = PulseEngine().run(ConsoleExample())

class ConsoleExample : Game()
{
    override fun init()
    {
        engine.gfx.mainSurface.setBackgroundColor(0.2f, 0.2f, 0.2f)
        engine.window.title = "Console Example"

        engine.console.registerCommand("echo {text:String}") {
            CommandResult(getString("text"))
        }
    }

    override fun update()
    {

    }

    override fun render()
    {
        engine.gfx.mainSurface.setDrawColor(1f,1f,1f)
        engine.gfx.mainSurface.drawText(
            text = "OPEN CONSOLE WITH F1",
            x = engine.window.width / 2f,
            y = engine.window.height / 2f,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
            fontSize = 72f)
    }

    override fun cleanup()
    {

    }
}

@ConsoleTarget
fun printBig(text: String): String = text.toUpperCase()