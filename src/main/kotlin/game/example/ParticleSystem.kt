package game.example

import engine.PulseEngine
import engine.GameEngine
import engine.modules.Game
import engine.data.Key
import engine.data.Mouse
import engine.data.ScreenMode.*
import engine.modules.graphics.BlendFunction
import engine.modules.graphics.postprocessing.effects.BloomEffect
import java.text.DecimalFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

fun main() = PulseEngine.run(ParticleSystem())

class ParticleSystem : Game()
{
    private var particles = FloatArray(DATA_FIELDS * MAX_PARTICLES)
    private var particleCount = 0
    private var useInterpolation = true
    private var renderMonoColor = true

    private val bloomEffect = BloomEffect(blurPasses = 2, blurRadius = 0.2f, exposure = 1.3f)

    companion object
    {
        const val X = 0
        const val Y = 1
        const val X_LAST = 2
        const val Y_LAST = 3
        const val X_VEL = 4
        const val Y_VEL = 5
        const val LIFE = 6
        const val DATA_FIELDS = 7
        const val MAX_PARTICLES = 4000000
    }

    override fun init()
    {
        engine.config.targetFps = 10000
        engine.config.fixedTickRate = 15
        engine.window.title = "Particle system"
        engine.gfx.setBlendFunction(BlendFunction.ADDITIVE)
        engine.gfx.setBackgroundColor(0.05f, 0.05f, 0.05f)
        engine.gfx.addPostProcessingEffect(bloomEffect)
    }

    override fun update()
    {
        if(engine.input.isPressed(Mouse.LEFT))
            spawnParticles(1000000f * engine.data.deltaTime, engine.input.xWorldMouse, engine.input.yWorldMouse)

        if(engine.input.wasClicked(Key.F))
            engine.window.updateScreenMode(if(engine.window.screenMode == WINDOWED) FULLSCREEN else WINDOWED)

        if(engine.input.wasClicked(Key.SPACE))
            useInterpolation = !useInterpolation

        if(engine.input.wasClicked(Key.C))
            renderMonoColor = !renderMonoColor

        if(engine.input.wasClicked(Key.R))
            particleCount = 0

        if(engine.input.isPressed(Mouse.MIDDLE))
        {
            engine.gfx.camera.xPos += engine.input.xdMouse
            engine.gfx.camera.yPos += engine.input.ydMouse
        }

        engine.gfx.camera.xScale += engine.input.scroll * 0.1f
        engine.gfx.camera.yScale += engine.input.scroll * 0.1f
    }

    private fun spawnParticles(amount: Float, x: Float, y: Float)
    {
        for(i in 0 until amount.toInt())
        {
            if(particleCount < MAX_PARTICLES -10)
            {
                val index = particleCount * DATA_FIELDS
                val angle = Random.nextFloat() * PI * 2
                val speed = Random.nextFloat() * 200f
                val life = 0.2f + 0.8f * Random.nextFloat()

                particles[index + X] = x
                particles[index + Y] = y
                particles[index + X_LAST] = x
                particles[index + Y_LAST] = y
                particles[index + X_VEL] = sin(angle).toFloat() * speed
                particles[index + Y_VEL] = cos(angle).toFloat() * speed
                particles[index + LIFE] = life
                particleCount++
            }
        }
    }

    override fun fixedUpdate()
    {
        updateParticles(engine)
    }

    private fun updateParticles(engine: GameEngine)
    {
        val mx = engine.input.xWorldMouse
        val my = engine.input.yWorldMouse
        val dt = engine.data.fixedDeltaTime
        val friction = (1.0f - 0.2f * dt)
        val gravity = 0f * dt
        val damage = 0.01f * dt
        val interact = engine.input.isPressed(Mouse.RIGHT)

        for(i in 0 until particleCount)
        {
            var srcIndex = i * DATA_FIELDS
            val dstIndex = srcIndex
            var life = particles[srcIndex + LIFE] - damage

            if (life <= 0)
            {
                if (particleCount > 1)
                {
                    srcIndex = (particleCount - 1) * DATA_FIELDS
                    life = particles[srcIndex + LIFE]
                }
                particleCount--
            }

            var xVel = particles[srcIndex + X_VEL] * friction
            var yVel = particles[srcIndex + Y_VEL] * friction
            val xPos = particles[srcIndex + X]
            val yPos = particles[srcIndex + Y]

            if(interact)
            {
                val xDelta = xPos - mx
                val yDelta = yPos - my
                val length = sqrt(xDelta * xDelta + yDelta * yDelta)
                if(length != 0f)
                {
                    val xDir = xDelta / length
                    val yDir = yDelta / length
                    val invLength = 1.0f - (length / 10000f)
                    xVel -= xDir * invLength * 450 * dt
                    yVel -= yDir * invLength * 450 * dt
                }
            }

            particles[dstIndex + X] = xPos + xVel * dt
            particles[dstIndex + Y] = yPos + yVel * dt
            particles[dstIndex + X_LAST] = xPos
            particles[dstIndex + Y_LAST] = yPos
            particles[dstIndex + X_VEL] = xVel
            particles[dstIndex + Y_VEL] = yVel
            particles[dstIndex + LIFE] = life - damage
        }
    }

    override fun render()
    {
        engine.gfx.setBlendFunction(BlendFunction.ADDITIVE)

        if(renderMonoColor)
            renderMonoColoredParticles(engine)
        else
            renderIndividualColoredParticles(engine)

        engine.gfx.camera.disable()
        engine.gfx.setColor(1f, 1f, 1f)
        engine.gfx.drawText("FPS: ${engine.data.currentFps}",  20f, 40f)
        engine.gfx.drawText("UPDATE: ${"%.1f".format(engine.data.updateTimeMS)} ms/f", 20f, 70f)
        engine.gfx.drawText("FIX_UPDATE: ${"%.1f".format(engine.data.fixedUpdateTimeMS)} ms/f", 20f, 100f)
        engine.gfx.drawText("RENDER: ${"%.1f".format(engine.data.renderTimeMs)} ms/f", 20f, 130f)
        engine.gfx.drawText("PARTICLES: ${DecimalFormat("#,###.##").format(particleCount)}", 20f, 160f)
    }

    private fun renderMonoColoredParticles(engine: GameEngine)
    {
        val dt = engine.data.fixedDeltaTime * 0.1f
        val interpolation = engine.data.interpolation
        val invInterpolation = 1.0f - engine.data.interpolation

        engine.gfx.setColor(1f, 0.4f, 0.1f)
        engine.gfx.drawSameColorLines { draw ->
            for (i in 0 until particleCount)
            {
                val index = i * DATA_FIELDS
                var x = particles[index + X]
                var y = particles[index + Y]

                if (useInterpolation)
                {
                    val xLast = particles[index + X_LAST]
                    val yLast = particles[index + Y_LAST]

                    if (x != xLast || y != yLast)
                    {
                        x = x * interpolation + xLast * invInterpolation
                        y = y * interpolation + yLast * invInterpolation
                    }
                }

                draw.line(x, y,
                    x + particles[index + X_VEL] * dt,
                    y + particles[index + Y_VEL] * dt
                )
            }
        }
    }

    private fun renderIndividualColoredParticles(engine: GameEngine)
    {
        val dt = engine.data.fixedDeltaTime * 0.1f
        val interpolation = engine.data.interpolation
        val invInterpolation = 1.0f - engine.data.interpolation

        for (i in 0 until particleCount)
        {
            val index = i * DATA_FIELDS
            var x = particles[index + X]
            var y = particles[index + Y]

            if (useInterpolation)
            {
                val xLast = particles[index + X_LAST]
                val yLast = particles[index + Y_LAST]

                if (x != xLast || y != yLast)
                {
                    x = x * interpolation + xLast * invInterpolation
                    y = y * interpolation + yLast * invInterpolation
                }
            }

            val fraction = i.toFloat() / particleCount
            engine.gfx.setColor(1.0f - fraction, fraction, 0.1f, particles[index + LIFE])
            engine.gfx.drawLine(x, y,
                x + particles[index + X_VEL] * dt,
                y + particles[index + Y_VEL] * dt
            )
        }
    }

    override fun cleanup()
    {
        println("Cleanup")
    }
}