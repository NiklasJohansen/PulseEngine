package game.example

import engine.Engine
import engine.EngineInterface
import engine.GameContext
import engine.data.Key
import engine.data.Mouse
import engine.modules.BlendFunction
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
        engine.targetFps = 120

        // Load assets from disc
        engine.asset.load("/textAsset.txt", "text_asset", Text::class.java)
        engine.asset.load("/imageAsset.png", "image_asset", Image::class.java)
    }

    override fun update(engine: EngineInterface)
    {
        // Mouse buttons
        if(engine.input.isPressed(Mouse.LEFT))
            println("Left mouse pressed")

        // Keyboard keys
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
        engine.gfx.setBackgroundColor(0.7f, 0.7f, 0.7f)

        // Set blending function
        engine.gfx.setBlendFunction(BlendFunction.NORMAL)

        // Set draw color
        engine.gfx.setColor(1f, 1f, 1f)

        // Get window size
        val width  = engine.window.width.toFloat()
        val height = engine.window.height.toFloat()

        // Draw single lines
        engine.gfx.drawLine(0f, 0f,  engine.input.xMouse, engine.input.yMouse)
        engine.gfx.drawLine(width, 0f,  engine.input.xMouse, engine.input.yMouse)
        engine.gfx.drawLine(width, height,  engine.input.xMouse, engine.input.yMouse)
        engine.gfx.drawLine(0f, height,  engine.input.xMouse, engine.input.yMouse)

        // Draw multiple lines
        engine.gfx.drawLines(floatArrayOf(
            200f, 0f, 200f, height,            // x0, y0, x1, y1 of first line
            width-200f, 0f, width-200, height  // x0, y0, x1, y1 of second line
        ))

        // Get loaded image asset
        engine.asset.get<Image>("image_asset")?.let { image ->

            // Set draw color
            engine.gfx.setColor(1f, 1f, 1f, 0.9f)

            // Draw loaded image
            engine.gfx.drawImage(image, engine.input.xMouse, engine.input.yMouse, size, size, xOrigin = 0.5f, yOrigin = 0.5f, rot = angle)
        }
    }

    override fun cleanUp(engine: EngineInterface)
    {
        println("Cleaning up example...")
    }
}