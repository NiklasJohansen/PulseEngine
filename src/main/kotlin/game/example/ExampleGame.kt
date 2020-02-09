package game.example

import engine.Engine
import engine.EngineInterface
import engine.GameContext
import engine.data.Key
import engine.data.Mouse
import engine.modules.Image
import engine.modules.Text
import kotlin.math.PI
import kotlin.math.sin

fun main()
{
    Engine().run(ExampleGame())
}

class ExampleGame : GameContext
{
    var size: Float = 200f
    var angle: Float = 200f

    override fun init(engine: EngineInterface)
    {
        // Set game loop target FPS
        engine.targetFps = 10000

        // Load assets from disc
        engine.asset.load("/textAsset.txt", "text_asset", Text::class.java)
        engine.asset.load("/imageAsset.png", "image_asset", Image::class.java)
    }

    override fun update(engine: EngineInterface)
    {
        // Keyboard key check
        if(engine.input.isPressed(Mouse.LEFT))
            println("Left mouse pressed")

        // Mouse button
        if(engine.input.isPressed(Key.SPACE))
            println("Space pressed")

        // Get loaded asset
        val textAsset = engine.asset.get<Text>("text_asset")

        // Set window title
        engine.window.title = "Fps: ${engine.currentFps} - ${textAsset?.text}"

        // Update game parameters
        angle = (angle + 1) % 360
        size = sin(angle / 360f * PI).toFloat() * 200f
    }

    override fun render(engine: EngineInterface)
    {
        // Set color of background
        engine.gfx.setBackgroundColor(0.8f, 0.8f, 0.8f)

        // Set draw color and draw line
        engine.gfx.setColor(1f, 0f, 0f)
        engine.gfx.drawLine(200f, 200f,  engine.input.xMouse, engine.input.yMouse, 2f)

        // Get loaded image asset
        engine.asset.get<Image>("image_asset")?.let { image ->

            // Set render color
            engine.gfx.setColor(1f, 1f, 1f)

            // Draw load image
            engine.gfx.drawImage(image, engine.input.xMouse, engine.input.yMouse, size, size, xOrigin = 0.5f, yOrigin = 0.5f, rot = angle)
        }
    }

    override fun cleanUp(engine: EngineInterface)
    {
        println("Clean up stuff")
    }
}