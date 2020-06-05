package game.example

import engine.PulseEngine
import engine.modules.Game
import engine.data.*
import engine.data.ScreenMode.*
import engine.modules.entity.Transform2D
import engine.modules.graphics.BlendFunction
import engine.modules.graphics.postprocessing.effects.BloomEffect
import engine.modules.graphics.postprocessing.effects.VignetteEffect
import engine.util.interpolateFrom
import kotlin.math.PI
import kotlin.math.sin

fun main() = PulseEngine().run(FeatureExample())

class FeatureExample : Game()
{
    private var size: Float = 200f
    private var lastSize: Float = 200f
    private var angle: Float = 200f
    private var lastAngle: Float = 200f
    private var boxPosition = Transform2D()
    private var lastBoxPosition = Transform2D()
    private var frame = 0f
    private var lastFrame = 0f

    override fun init()
    {
        // Load assets from disc
        engine.asset.loadText("/textAsset.txt", "text_asset")
        engine.asset.loadTexture("/imageAsset.png", "image_asset")
        engine.asset.loadSpriteSheet("/coin.png", "sprite_sheet_asset", 6, 1)
        engine.asset.loadFont("/FiraSans-Regular.ttf", "font_asset", floatArrayOf(24f, 72f))

        // Load configuration from disc
        engine.config.load("/example.config")

        // Get config properties
        val number = engine.config.getInt("numberProp")
        val text = engine.config.getString("textProp")
        val bool = engine.config.getBool("boolProp")
        println("From loaded config: $number, $text, $bool")

        // Set game loop target FPS
        engine.config.targetFps = 120

        // Set the tick rate of the fixed update
        engine.config.fixedTickRate = 60

        // Set starting position of movable box
        boxPosition.x = engine.window.width / 2f
        boxPosition.y = engine.window.height / 2f
        lastBoxPosition.x = boxPosition.x
        lastBoxPosition.y = boxPosition.y

        // Set camera smooth
        engine.gfx.mainCamera.targetTrackingSmoothing = 1f

        // Add separate UI graphics layer for text
        engine.gfx.createSurface2D("text")

        // Add post processing effects
        engine.gfx.mainSurface.addPostProcessingEffect(BloomEffect())
        engine.gfx.mainSurface.addPostProcessingEffect(VignetteEffect())
    }

    // Runs every frame
    override fun update()
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
        if(engine.input.wasClicked(Key.F) && engine.input.isPressed(Key.LEFT_CONTROL))
            engine.window.updateScreenMode(if(engine.window.screenMode == WINDOWED) FULLSCREEN else WINDOWED)

        // Get loaded asset
        val textAsset = engine.asset.get<Text>("text_asset")

        // Set window title
        engine.window.title = "Fps: ${engine.data.currentFps} - ${textAsset.text}"

        val dt = engine.data.deltaTime

        if(engine.input.isPressed(Key.K_1)) engine.gfx.mainCamera.zRot -= 1 * dt
        if(engine.input.isPressed(Key.K_2)) engine.gfx.mainCamera.zRot += 1 * dt
        if(engine.input.isPressed(Mouse.MIDDLE))
        {
            engine.gfx.mainCamera.xPos += engine.input.xdMouse
            engine.gfx.mainCamera.yPos += engine.input.ydMouse
        }

        engine.gfx.mainCamera.xScale += engine.input.scroll * 0.1f
        engine.gfx.mainCamera.yScale += engine.input.scroll * 0.1f
        engine.gfx.mainCamera.xOrigin = engine.window.width * 0.5f
        engine.gfx.mainCamera.yOrigin = engine.window.height * 0.5f
    }

    // Runs at a fixed rate
    override fun fixedUpdate()
    {
        // Fixed delta time equals: 1.0 / fixedTickRate
        val dt = engine.data.fixedDeltaTime

        // Update game parameters
        lastAngle = angle
        lastSize = size
        lastFrame = frame
        angle = (angle + 100 * dt) % 360
        size = sin(angle / 360f * PI).toFloat() * 200f
        frame += 10 * dt

        // Update box position
        lastBoxPosition.x = boxPosition.x
        lastBoxPosition.y = boxPosition.y
        if(engine.input.isPressed(Key.W)) boxPosition.y -= 400 * dt
        if(engine.input.isPressed(Key.A)) boxPosition.x -= 400 * dt
        if(engine.input.isPressed(Key.S)) boxPosition.y += 400 * dt
        if(engine.input.isPressed(Key.D)) boxPosition.x += 400 * dt
    }

    override fun render()
    {
        val surface = engine.gfx.mainSurface

        // Set camera target
        engine.gfx.mainCamera.setTarget(boxPosition)

        // Set color of background
        surface.setBackgroundColor(0.1f, 0.1f, 0.1f)

        // Set blending function
        surface.setBlendFunction(BlendFunction.NORMAL)

        // Get window size
        val width  = engine.window.width.toFloat()
        val height = engine.window.height.toFloat()

        // Get mouse position
        val xMouse = engine.input.xWorldMouse
        val yMouse = engine.input.yWorldMouse

        // Interpolated position for smooth movement
        val x = boxPosition.x.interpolateFrom(lastBoxPosition.x)
        val y = boxPosition.y.interpolateFrom(lastBoxPosition.y)
        val size = size.interpolateFrom(lastSize)
        val angle = angle.interpolateFrom( lastAngle)
        val frame = frame.interpolateFrom(lastFrame)

        // Draw colored lines
        for(i in 0 until width.toInt())
        {
            val c =  i / width
            surface.setDrawColor(1-c,  c, 0f)
            surface.drawLinePoint(i.toFloat(), 0f)
            surface.setDrawColor(0f,  1-c, c)
            surface.drawLinePoint(i.toFloat(), height)
        }

        // Set draw color
        surface.setDrawColor(1f, 0f, 0f)

        // Draw single line
        surface.drawLine(size, size,  xMouse, yMouse)

        // Draw multiple lines
        surface.drawSameColorLines { draw ->
            draw.line(size, height-size, xMouse, yMouse)
            draw.line(width-size, height-size, xMouse, yMouse)
            draw.line(width-size, size, xMouse, yMouse)
        }

        // Use text UI layer
        val textSurface = engine.gfx.getSurface2D("text")

        // Draw text
        val font = engine.asset.get<Font>("font_asset")
        textSurface.setDrawColor(1f, 0f, 0f)
        textSurface.drawText("FPS: ${engine.data.currentFps}", width / 2f - 70, 20f, font, fontSize = 24f)
        textSurface.drawText("BIG TEXT", width / 2f, height / 2, font, xOrigin = 0.5f, yOrigin = 0.5f, fontSize = 72f)

        // Set color to tint image
        surface.setDrawColor(0.7f, 0.7f, 1f, 0.9f)

        // Get loaded image asset
        val image = engine.asset.get<Texture>("image_asset")

        // Draw images
        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(image, 0.5f, 0.5f, size, size)
        surface.drawTexture(image, width-size, 0.5f, size, size)
        surface.drawTexture(image, width-size, height-size, size, size)
        surface.drawTexture(image, 0.5f, height-size, size, size)
        surface.drawTexture(image, xMouse, yMouse, size, size, xOrigin = 0.5f, yOrigin = 0.5f, rot = angle)

        // Get sprite sheet and frame texture
        val coinSpriteSheet = engine.asset.get<SpriteSheet>("sprite_sheet_asset")
        val frameTexture = coinSpriteSheet.getTexture(frame.toInt() % 6)

        // Draw frame texture
        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(frameTexture, x - 25f, y - 25f, 50f, 50f)
    }

    override fun cleanup()
    {
        println("Cleaning up example...")
    }
}