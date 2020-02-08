package game.cave

import engine.Engine
import engine.EngineInterface
import engine.GameContext
import engine.data.*
import kotlin.math.abs
import kotlin.math.max

fun main()
{
    Engine().run(CaveGame())
}

class CaveGame : GameContext
{
    private var xCam = -16530f
    private var yCam = -25970f
    private var blockWidth = 40
    private var blockHeight = 40
    private var nOctaves = 4
    private var blockTypes = arrayOf(
        Block(0.08f,  Color(0.588f, 0.78f, 1.0f),   false), // Sky
        Block(0.115f, Color(0.196f, 0.588f, 0.117f),true),  // Grass
        Block(0.3f,   Color(0.176f, 0.113f, 0.094f), true), // Dirt
        Block(0.4f,   Color(0.321f, 0.317f, 0.309f), false) // Stone
    )

    override fun init(engine: EngineInterface)
    {
        engine.targetFps = 1000
    }

    override fun update(engine: EngineInterface)
    {
        if(engine.input.isPressed(Mouse.LEFT))
        {
            xCam += engine.input.xdMouse
            yCam += engine.input.ydMouse
        }

        if(engine.input.scroll != 0)
        {
            blockWidth = max(1, blockWidth + engine.input.scroll)
            blockHeight = max(1, blockHeight + engine.input.scroll)
            println(blockWidth)
        }

        engine.input.gamepads.forEach { gamepad ->
            val xLeft = gamepad.getAxis(Axis.LEFT_X)
            val yLeft = gamepad.getAxis(Axis.LEFT_Y)
            xCam -= if (abs(xLeft) > 0.15f) (xLeft-0.15f) * 10 else 0.0f
            yCam -= if (abs(yLeft) > 0.15f) (yLeft-0.15f) * 10 else 0.0f
        }

        if(engine.input.isPressed(Key.W)) yCam += 10
        if(engine.input.isPressed(Key.A)) xCam += 10
        if(engine.input.isPressed(Key.S)) yCam -= 10
        if(engine.input.isPressed(Key.D)) xCam -= 10

        if(engine.input.gamepads.firstOrNull()?.isPressed(Button.A) == true)
            blockWidth += 1

        if(engine.input.gamepads.firstOrNull()?.isPressed(Button.B) == true)
            blockWidth -= 1

        engine.window.title = "FPS: ${engine.currentFps}  X: $xCam Y:$yCam"
    }

    override fun render(engine: EngineInterface)
    {
        val xCellCount = engine.window.width / blockWidth
        val yCellCount = engine.window.height / blockHeight

        for (y in -1 until yCellCount + 2)
        {
            for (x in -1 until xCellCount + 2)
            {
                val c = getColor(
                    x - (xCam.toInt() / blockWidth).toFloat(),
                    y - (yCam.toInt() / blockHeight).toFloat()
                )

                val xBlock = x * blockWidth + xCam % blockWidth
                val yBlock = y * blockHeight + yCam % blockHeight

                engine.gfx.setColor(c.red, c.green, c.blue, 0.1f)
                engine.gfx.drawQuad(xBlock, yBlock, blockWidth.toFloat(), blockHeight.toFloat())
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

    override fun cleanUp(engine: EngineInterface) {}
}

data class Color(val red: Float, val green: Float, val blue: Float)

data class Block(val density: Float, val color: Color, val blend: Boolean)