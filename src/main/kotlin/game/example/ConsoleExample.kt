package game.example

import engine.PulseEngine
import engine.modules.console.CommandResult
import engine.modules.Game
import engine.modules.console.ConsoleTarget
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun main() = PulseEngine().run(ConsoleExample())

class ConsoleExample : Game()
{
    override fun init()
    {
        engine.gfx.setBackgroundColor(0.2f, 0.2f, 0.2f)
        engine.window.title = "Console Example"

        engine.console.registerCommand("echo {text:String}") {
            CommandResult(getString("text"))
        }

        engine.console.registerCommand("exit") {
            engine.window.close()
            CommandResult("Exiting")
        }

        engine.console.registerCommand("delay {sec:Float} {command:String}") {
            val seconds = getFloat("sec")
            GlobalScope.launch {
                delay((seconds*1000f).toLong())
                engine.console.run(getString("command"))
            }
            CommandResult("Running command after $seconds seconds")
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