package game.example

import engine.Engine
import engine.EngineInterface
import engine.abstraction.GameContext
import engine.data.Font
import engine.data.Image
import engine.data.Key
import engine.data.Mouse
import engine.data.ScreenMode.*
import engine.modules.graphics.BlendFunction
import engine.data.Text
import kotlin.math.PI
import kotlin.math.sin

fun main()
{
    Engine().run(FeatureExample())
}

class FeatureExample : GameContext
{
    private var size: Float = 200f
    private var angle: Float = 200f

    override fun init(engine: EngineInterface)
    {
        // Load assets from disc
        engine.asset.loadText("/textAsset.txt", "text_asset")
        engine.asset.loadImage("/imageAsset.png", "image_asset")
        engine.asset.loadFont("/FiraSans-Regular.ttf", "font_asset", floatArrayOf(24f, 72f))

        // Load configuration from disc
        engine.config.load("/example.config")

        // Get config properties
        val number = engine.config.getInt("numberProp")
        val text = engine.config.getString("textProp")
        val bool = engine.config.getBool("boolProp")
        println("From loaded config: $number, $text, $bool")

        // Set game loop target FPS
        engine.config.targetFps = 1000

        // Set the tick rate of the fixed update
        engine.config.fixedTickRate = 50
    }

    // Runs every frame
    override fun update(engine: EngineInterface)
    {
        // Mouse clicked
        if(engine.input.wasClicked(Mouse.LEFT))
            println("Left mouse clicked once")

        // Keyboard pressed
        if(engine.input.isPressed(Key.SPACE))
            println("Space is pressed")

        // Mouse released
        if(engine.input.wasReleased(Mouse.RIGHT))
            println("Right mouse released")

        // Key F to change screen mode
        if(engine.input.wasClicked(Key.F))
            engine.window.updateScreenMode(if(engine.window.screenMode == WINDOWED) FULLSCREEN else WINDOWED)

        // Get loaded asset
        val textAsset = engine.asset.get<Text>("text_asset")

        // Set window title
        engine.window.title = "Fps: ${engine.data.currentFps} - ${textAsset.text}"
    }

    // Runs at a fixed rate
    override fun fixedUpdate(engine: EngineInterface)
    {
        // Fixed delta time equals: 1.0 / fixedTickRate
        val dt = engine.data.fixedDeltaTime

        // Update game parameters
        angle = (angle + 100 * dt) % 360
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

        // Draw text
        val font = engine.asset.get<Font>("font_asset")
        engine.gfx.drawText("FPS: ${engine.data.currentFps}", width / 2f - 70, 20f, font, fontSize = 24f)
        engine.gfx.drawText("ROTATING TEXT", width / 2f, height / 2, font,  rotation = angle, xOrigin = 0.5f, yOrigin = 0.5f, fontSize = 72f)

        // Draw single line
        engine.gfx.drawLine(size, size,  engine.input.xMouse, engine.input.yMouse)

        // Get loaded image asset
        val image = engine.asset.get<Image>("image_asset")

        // Set color to tint image
        engine.gfx.setColor(0.7f, 0.7f, 1f, 0.9f)

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

    override fun cleanUp(engine: EngineInterface)
    {
        println("Cleaning up example...")
    }
}