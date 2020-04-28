package game.example

import engine.PulseEngine
import engine.modules.CommandResult
import engine.modules.Game

fun main() = PulseEngine.run(ConsoleExample())

class ConsoleExample : Game()
{
    override fun init()
    {
        engine.gfx.setBackgroundColor(0.2f, 0.2f, 0.2f)
        engine.window.title = "Console Example"

        engine.console.registerCommand("echo {text:String}")
        {
            CommandResult(it.getString("text"))
        }

        engine.console.registerCommand("exit")
        {
            engine.window.close()
            CommandResult("Exiting")
        }
    }

    override fun update()
    {

    }

    override fun render()
    {
        engine.gfx.setColor(1f,1f,1f)
        engine.gfx.drawText(
            text = "OPEN CONSOLE WITH F1",
            x = engine.window.width / 2f,
            y = engine.window.height/2f,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
            fontSize = 72f)
    }

    override fun cleanup()
    {

    }
}