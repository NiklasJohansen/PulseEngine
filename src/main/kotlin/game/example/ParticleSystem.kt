package game.example

import engine.Engine
import engine.EngineInterface
import engine.GameContext
import engine.data.Key
import engine.data.Mouse
import engine.data.ScreenMode.*
import engine.modules.rendering.BlendFunction
import java.text.DecimalFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

fun main()
{
    Engine().run(ParticleSystem())
}

class ParticleSystem : GameContext
{
    private var particles = FloatArray(DATA_FIELDS * MAX_PARTICLES)
    private var particleCount = 0
    private var useInterpolation = true

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
        const val MAX_PARTICLES = 1000000
    }

    override fun init(engine: EngineInterface)
    {
        engine.config.targetFps = 120
        engine.config.fixedTickRate = 15
        engine.window.title = "SpeedParticles"
        engine.gfx.setBlendFunction(BlendFunction.ADDITIVE)
        engine.gfx.setBackgroundColor(0.1f, 0.1f, 0.1f)
    }

    override fun update(engine: EngineInterface)
    {
        createParticles(engine)

        if(engine.input.wasClicked(Key.F))
            engine.window.updateScreenMode(if(engine.window.screenMode == WINDOWED) FULLSCREEN else WINDOWED)

        if(engine.input.wasClicked(Key.SPACE))
            useInterpolation = !useInterpolation
    }

    override fun fixedUpdate(engine: EngineInterface)
    {
        updateParticles(engine)
    }

    private fun createParticles(engine: EngineInterface)
    {
        val dt = engine.data.deltaTime
        if(engine.input.isPressed(Mouse.LEFT))
        {
            val particlesPerSec = 100000 * dt
            for(i in 0 until particlesPerSec.toInt())
            {
                if(particleCount < MAX_PARTICLES -10)
                {
                    val index = particleCount * DATA_FIELDS
                    val angle = Random.nextFloat() * PI * 2
                    val speed = Random.nextFloat() * 200f
                    val life = 0.2f + 0.8f * Random.nextFloat()
                    val x = engine.input.xMouse
                    val y = engine.input.yMouse

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
    }

    private fun updateParticles(engine: EngineInterface)
    {
        val mx = engine.input.xMouse
        val my = engine.input.yMouse
        val dt = engine.data.fixedDeltaTime
        val friction = (1.0f - 0.2f * dt)
        val gravity = 0f * dt
        val damage = 0.01f * dt
        val interact = engine.input.isPressed(Mouse.RIGHT)

        for(i in 0 until particleCount)
        {
            val dstIndex = i * DATA_FIELDS
            var srcIndex = i * DATA_FIELDS
            var life = particles[srcIndex + LIFE] - damage

            if (life < 0)
            {
                if (particleCount > 1)
                {
                    srcIndex = (particleCount - 1) * DATA_FIELDS
                    life = particles[srcIndex + LIFE]
                }
                particleCount--
            }

            var xVel = particles[srcIndex + X_VEL] * friction
            var yVel = particles[srcIndex + Y_VEL] * friction + gravity
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
                    val invLength = 1.0f - (length / 2000f)
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

    override fun render(engine: EngineInterface)
    {
        val dt = engine.data.fixedDeltaTime * 0.2f
        val interpolation =  engine.data.interpolation

        engine.gfx.drawLines {  draw ->
            for(i in 0 until particleCount)
            {
                val index = i * DATA_FIELDS
                var x = particles[index + X]
                var y = particles[index + Y]

                if(useInterpolation)
                {
                    val xLast = particles[index + X_LAST]
                    val yLast = particles[index + Y_LAST]

                    if(x == xLast && y == yLast)
                        continue

                    x = x * interpolation + xLast * (1.0f - interpolation)
                    y = y * interpolation + yLast * (1.0f - interpolation)
                }

                draw.color(1f, 0.4f, 0.1f, particles[index + LIFE])
                draw.line(x, y,
                    x + particles[index + X_VEL] * dt,
                    y + particles[index + Y_VEL] * dt
                )
            }
        }

        engine.gfx.setColor(1f, 1f, 1f)
        engine.gfx.drawText("FPS: ${engine.data.currentFps}",  20f, 40f)
        engine.gfx.drawText("UPDATE: ${"%.1f".format(engine.data.updateTimeMS)} ms", 20f, 70f)
        engine.gfx.drawText("FIX_UPDATE: ${"%.1f".format(engine.data.fixedUpdateTimeMS)} ms", 20f, 100f)
        engine.gfx.drawText("RENDER: ${"%.1f".format(engine.data.renderTimeMs)} ms", 20f, 130f)
        engine.gfx.drawText("PARTICLES: ${DecimalFormat("#,###.##").format(particleCount)}", 20f, 160f)
    }

    override fun cleanUp(engine: EngineInterface)
    {
        println("Cleanup")
    }
}