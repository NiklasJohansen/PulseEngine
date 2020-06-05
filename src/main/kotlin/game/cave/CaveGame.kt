package game.cave

import engine.PulseEngine
import engine.modules.Game
import engine.data.*
import engine.modules.graphics.postprocessing.effects.BloomEffect
import engine.modules.graphics.postprocessing.effects.VignetteEffect
import engine.util.Camera2DController
import org.joml.Math.sin
import kotlin.math.abs

fun main() = PulseEngine.run(CaveGame())

class CaveGame : Game()
{
    private var xCam = -16530f
    private var yCam = -25970f

    private var angle = 0f
    private var blockWidth = 40f
    private var blockHeight = 40f
    private var blockCount = 0f
    private var nOctaves = 4
    private var blockTypes = arrayOf(
        Block(0.08f,  Color(0.588f, 0.78f, 1.0f),   false), // Sky
        Block(0.115f, Color(0.196f, 0.588f, 0.117f),true),  // Grass
        Block(0.3f,   Color(0.176f, 0.113f, 0.094f), true), // Dirt
        Block(0.4f,   Color(0.321f, 0.317f, 0.309f), false) // Stone
    )

    private val bloomEffect = BloomEffect()
    private val vignetteEffect = VignetteEffect()
    private val cameraController = Camera2DController(Mouse.LEFT, minScale = 0.05f, smoothing = 0f)

    override fun init()
    {
        engine.config.targetFps = 120
        engine.window.title = "Cave Game"
        engine.data.addSource("BLOCKS", "COUNT") { blockCount }
        engine.gfx.mainSurface
            .addPostProcessingEffect(bloomEffect)
            .addPostProcessingEffect(vignetteEffect)
    }

    override fun update()
    {
        val dt = engine.data.deltaTime

        if(engine.input.gamepads.firstOrNull()?.isPressed(Button.A) == true)
            blockWidth += 1 * dt

        if(engine.input.gamepads.firstOrNull()?.isPressed(Button.B) == true)
            blockWidth -= 1 * dt

        engine.input.gamepads.forEach { gamepad ->
            val xLeft = gamepad.getAxis(Axis.LEFT_X)
            val yLeft = gamepad.getAxis(Axis.LEFT_Y)
            engine.gfx.mainSurface.camera.xPos -= if (abs(xLeft) > 0.15f) (xLeft-0.15f) * 10 * dt else 0.0f
            engine.gfx.mainSurface.camera.yPos -= if (abs(yLeft) > 0.15f) (yLeft-0.15f) * 10 * dt else 0.0f
        }

        if(engine.input.isPressed(Mouse.MIDDLE))
            bloomEffect.exposure -= engine.input.ydMouse / 10f

        if(engine.input.isPressed(Mouse.RIGHT))
            vignetteEffect.strength -= engine.input.ydMouse / 10f

        if(engine.input.wasClicked(Key.UP))
            bloomEffect.blurPasses++

        if(engine.input.wasClicked(Key.DOWN))
            bloomEffect.blurPasses--

        cameraController.update(engine)

        val worldPos = engine.gfx.mainSurface.camera.screenPosToWorldPos(engine.window.width / 2f, engine.window.height / 2f)
        xCam = worldPos.x
        yCam = worldPos.y
    }

    override fun fixedUpdate()
    {
        engine.gfx.mainSurface.camera.xPos -= 100f * engine.data.fixedDeltaTime
        engine.gfx.mainSurface.camera.yPos -= sin(angle) * 100f * engine.data.fixedDeltaTime
        angle += 0.5f * engine.data.fixedDeltaTime
    }

    override fun render()
    {
        val surface = engine.gfx.mainSurface
        val blockWidthInt = blockWidth.toInt()
        val blockHeightInt = blockHeight.toInt()
        val windowWidth = engine.window.width / surface.camera.xScale
        val windowHeight = engine.window.height / surface.camera.yScale

        val xStart = xCam - windowWidth / 2f - blockWidth
        val yStart = yCam - windowHeight / 2f - blockHeight
        val xEnd = xCam + windowWidth / 2f + blockWidth
        val yEnd = yCam + windowHeight / 2f + blockHeight

        blockCount = 0f
        var y = yStart
        while ( y < yEnd )
        {
            var x = xStart
            while (x < xEnd)
            {
                val xBlock = (x / blockWidthInt).toInt().toFloat()
                val yBlock = (y / blockHeightInt).toInt().toFloat()

                val c = getColor(xBlock, yBlock)

                // val c = Color((xBlock % 10) / 10f, (yBlock % 10) / 10f, ((xBlock+yBlock) % 10) / 10f)

                surface.setDrawColor(c.red, c.green, c.blue)
                surface.drawQuad(x - (x.toInt() % blockWidthInt), y - (y.toInt() % blockHeightInt), blockWidth, blockHeight)

                blockCount++
                x += blockWidthInt
            }
            y += blockHeightInt
        }
    }

    private fun getColor(x: Float, y: Float): Color
    {
        val density = 1.0f - NoiseGenerator.getRidgedFractalNoise(x, y, nOctaves)

        for(i in 0 until blockTypes.size - 1)
        {
            val (thisDens, thisColor, thisBlend) = blockTypes[i]
            if(density > thisDens)
                continue

            return if (thisBlend && i > 0)
            {
                val nextColor = blockTypes[i + 1].color
                val prevDens = blockTypes[i-1].density
                lerpColor(thisColor, nextColor, (density - prevDens) / (thisDens - prevDens))
            }
            else thisColor
        }

        return blockTypes.last().color
    }

    private fun lerpColor(a: Color, b: Color, t: Float): Color
    {
        return Color(
            red = a.red * (1.0f - t) + b.red * t,
            green = a.green * (1.0f - t) + b.green * t,
            blue = a.blue * (1.0f - t) + b.blue * t
        )
    }

    override fun cleanup()
    {
        println("Cleaning up CaveGame...")
    }
}

data class Block(val density: Float, val color: Color, val blend: Boolean)