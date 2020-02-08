package game.example

import engine.Engine
import engine.EngineInterface
import engine.GameContext
import engine.data.Key
import engine.data.Mouse
import engine.modules.Text

fun main()
{
    Engine().run(ExampleGame())
}

class ExampleGame : GameContext
{
    override fun init(engine: EngineInterface)
    {
        engine.targetFps = 120
        engine.asset.load("/exampleAsset.txt", "asset", Text::class.java)
    }

    override fun update(engine: EngineInterface)
    {
        if(engine.input.isPressed(Mouse.LEFT))
            println("Left mouse pressed")

        if(engine.input.isPressed(Key.SPACE))
            println("Space pressed")

        val asset = engine.asset.get<Text>("asset")
        println(asset?.text)

        engine.window.title = "Fps: ${engine.currentFps}"
    }

    override fun render(engine: EngineInterface)
    {
        engine.gfx.setColor(1f, 1f, 1f, 1f)
        engine.gfx.drawQuad(engine.input.xMouse - 25, engine.input.yMouse - 25, 50f, 50f)
    }

    override fun cleanUp(engine: EngineInterface)
    {
        println("Clean up stuff")
    }
}