package game.example

import engine.Engine
import engine.EngineInterface
import engine.GameContext
import engine.data.Key
import engine.data.Mouse
import engine.modules.rendering.BlendFunction
import engine.modules.Image
import engine.modules.Text
import kotlin.math.PI
import kotlin.math.cos
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

        // Get window size
        val width  = engine.window.width.toFloat()
        val height = engine.window.height.toFloat()

        // Draw multiple lines
        engine.gfx.drawLines { draw ->
            for(i in 0 until width.toInt())
            {
                val c =  i / width
                draw.color(1-c,  c, 0f)
                draw.linePoint(i.toFloat(), 0f)
                draw.color(0f,  1-c, c)
                draw.linePoint(i.toFloat(), height)
            }
            draw.color(1f,1f,1f)
            draw.line(size, height-size,  engine.input.xMouse, engine.input.yMouse)
            draw.line(width-size, height-size,  engine.input.xMouse, engine.input.yMouse)
            draw.line(width-size, size,  engine.input.xMouse, engine.input.yMouse)
        }

        // Set draw color
        engine.gfx.setColor(1f, 1f, 1f)

        // Draw single line
        engine.gfx.drawLine(size, size,  engine.input.xMouse, engine.input.yMouse)

        // Get loaded image asset
        engine.asset.get<Image>("image_asset")?.let { image ->

            // Set draw color
            engine.gfx.setColor(1f, 1f, 1f, 0.9f)

            // Draw single loaded image
            engine.gfx.drawImage(image, engine.input.xMouse, engine.input.yMouse, size, size, xOrigin = 0.5f, yOrigin = 0.5f, rot = angle)

            // Draw loaded image multiple times
            engine.gfx.drawImages(image) { draw ->
                draw.color(1f, 1f, 1f)
                draw.image(0.5f, 0.5f, size, size)
                draw.image(width-size, 0.5f, size, size)
                draw.image(width-size, height-size, size, size)
                draw.image(0.5f, height-size, size, size)
            }
        }
    }

    override fun cleanUp(engine: EngineInterface)
    {
        println("Cleaning up example...")
    }
}