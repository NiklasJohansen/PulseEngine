package game.cave

import engine.PulseEngine
import engine.modules.Game
import engine.data.*
import kotlin.math.abs
import kotlin.math.max

fun main() = PulseEngine.run(CaveGame())

class CaveGame : Game()
{
    private var xCam = -16530f
    private var yCam = -25970f
    private var blockWidth = 40f
    private var blockHeight = 40f
    private var nOctaves = 4
    private var blockTypes = arrayOf(
        Block(0.08f,  Color(0.588f, 0.78f, 1.0f),   false), // Sky
        Block(0.115f, Color(0.196f, 0.588f, 0.117f),true),  // Grass
        Block(0.3f,   Color(0.176f, 0.113f, 0.094f), true), // Dirt
        Block(0.4f,   Color(0.321f, 0.317f, 0.309f), false) // Stone
    )

    override fun init()
    {
        engine.config.targetFps = 120
    }

    override fun update()
    {
        engine.window.title = "FPS: ${engine.data.currentFps}  X: $xCam  Y:$yCam  U: ${"%.2f".format(engine.data.updateTimeMS)}ms  R: ${"%.2f".format(engine.data.renderTimeMs)}ms"

        val dt = engine.data.deltaTime

        if(engine.input.scroll != 0)
        {
            blockWidth = max(1f, blockWidth + engine.input.scroll)
            blockHeight = max(1f, blockHeight + engine.input.scroll)
            println(blockWidth)
        }

        if(engine.input.isPressed(Mouse.LEFT))
        {
            xCam += engine.input.xdMouse
            yCam += engine.input.ydMouse
        }

        if(engine.input.isPressed(Key.W)) engine.gfx.camera.yPos += 100 * dt
        if(engine.input.isPressed(Key.A)) engine.gfx.camera.xPos += 100 * dt
        if(engine.input.isPressed(Key.S)) engine.gfx.camera.yPos -= 100 * dt
        if(engine.input.isPressed(Key.D)) engine.gfx.camera.xPos -= 100 * dt

        engine.gfx.camera.xOrigin = engine.window.width / 2f
        engine.gfx.camera.yOrigin = engine.window.height / 2f

        if(engine.input.isPressed(Key.K_1)) engine.gfx.camera.zRot -= 1 * dt
        if(engine.input.isPressed(Key.K_2)) engine.gfx.camera.zRot += 1 * dt

        if(engine.input.isPressed(Key.K_3)) engine.gfx.camera.xScale -= 1f * dt
        if(engine.input.isPressed(Key.K_4)) engine.gfx.camera.xScale += 1f * dt

        if(engine.input.gamepads.firstOrNull()?.isPressed(Button.A) == true)
            blockWidth += 1 * dt

        if(engine.input.gamepads.firstOrNull()?.isPressed(Button.B) == true)
            blockWidth -= 1 * dt

        engine.input.gamepads.forEach { gamepad ->
            val xLeft = gamepad.getAxis(Axis.LEFT_X)
            val yLeft = gamepad.getAxis(Axis.LEFT_Y)
            xCam -= if (abs(xLeft) > 0.15f) (xLeft-0.15f) * 10 * dt else 0.0f
            yCam -= if (abs(yLeft) > 0.15f) (yLeft-0.15f) * 10 * dt else 0.0f
        }
    }

    override fun render()
    {
        val width = blockWidth.toInt()
        val height = blockHeight.toInt()

        val xCellCount = (engine.window.width / width)
        val yCellCount = (engine.window.height / height)

        for (y in -1 until yCellCount + 2)
        {
            for (x in -1 until xCellCount + 2)
            {
                val c = getColor(
                    x - (xCam.toInt() / width).toFloat(),
                    y - (yCam.toInt() / height).toFloat()
                )

                val xBlock = x * width + xCam % width
                val yBlock = y * height + yCam % height

                engine.gfx.setColor(c.red, c.green, c.blue)
                engine.gfx.drawQuad(xBlock, yBlock, width.toFloat(), height.toFloat())
            }
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

data class Color(val red: Float, val green: Float, val blue: Float)

data class Block(val density: Float, val color: Color, val blend: Boolean)