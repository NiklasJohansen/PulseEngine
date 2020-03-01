package game.example

import engine.Engine
import engine.EngineInterface
import engine.GameContext
import engine.data.Key
import engine.data.Mouse
import engine.data.ScreenMode
import engine.modules.entity.*
import engine.modules.rendering.BlendFunction
import game.cave.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

fun main()
{
    Engine().run(InterpolationExample())
}

// Press SPACE-key to disable interpolation

class InterpolationExample : GameContext
{
    override fun init(engine: EngineInterface)
    {
        // Game logic updates 15 times per second
        engine.config.fixedTickRate = 15
        engine.config.targetFps = 120
        engine.entity.registerSystems(
            UserInputSystem(),
            ParticlePhysicsSystem(),
            InterpolatedParticleRenderSystem(),
            ParticleHealthSystem()
        )
    }

    override fun update(engine: EngineInterface)
    {
        engine.window.title = "FPS: ${engine.data.currentFps}  Particles: ${engine.entity.count}"
        if(engine.input.wasClicked(Key.F))
            engine.window.updateScreenMode(if(engine.window.screenMode == ScreenMode.WINDOWED) ScreenMode.FULLSCREEN else ScreenMode.WINDOWED)
    }

    override fun fixedUpdate(engine: EngineInterface) { }

    override fun render(engine: EngineInterface)
    {
        engine.gfx.setBackgroundColor(0.1f, 0.1f, 0.1f)
    }

    override fun cleanUp(engine: EngineInterface) { }
}

// ------------------------------------------------- Components -------------------------------------------------

class ParticleStateComponent : Component()
{
    // Current state
    var x: Float = 0f
    var y: Float = 0f
    var xVel: Float = 0f
    var yVel: Float = 0f

    // Last state
    var xLast: Float = 0f
    var yLast: Float = 0f

    companion object { val type = ComponentType(ParticleStateComponent::class.java) }
}

class ParticleHealthComponent : Component()
{
    var amount: Float = 1f

    companion object { val type = ComponentType(ParticleHealthComponent::class.java) }
}

class ParticleColorComponent : Component()
{
    val color: Color = staticColor

    companion object
    {
        private val staticColor: Color = Color(1f, 0.4f, 0.1f)
        val type = ComponentType(ParticleColorComponent::class.java)
    }
}

// ------------------------------------------------- Systems -------------------------------------------------

class InterpolatedParticleRenderSystem : RenderSystem(ParticleStateComponent.type, ParticleHealthComponent.type, ParticleColorComponent.type)
{
    override fun render(engine: EngineInterface, entities: EntityCollection)
    {
        val dt = engine.data.fixedDeltaTime
        val interpolation = engine.data.interpolation
        val useInterpolation = !engine.input.isPressed(Key.SPACE)

        engine.gfx.setBlendFunction(BlendFunction.ADDITIVE)
        engine.gfx.setLineWidth(1f)
        engine.gfx.drawLines { draw ->
            for (entity in entities)
            {
                val transform = entity.getComponent(ParticleStateComponent.type)
                var x = transform.x
                var y = transform.y
                val xLast = transform.xLast
                val yLast = transform.yLast

                if(useInterpolation)
                {
                    if(x == xLast && y == yLast)
                        continue
                    x = x * interpolation + xLast * (1.0f - interpolation)
                    y = y * interpolation + yLast * (1.0f - interpolation)
                }

                val health = entity.getComponent(ParticleHealthComponent.type)
                val color = entity.getComponent(ParticleColorComponent.type)
                val fade = if(health.amount < 0.2f) health.amount / 0.2f else 1.0f

                draw.color(color.color.red, color.color.green, color.color.blue, 0.5f * fade)
                draw.line(x, y, x + transform.xVel*dt, y + transform.yVel*dt)
            }
        }
    }
}

class ParticlePhysicsSystem : LogicSystem(ParticleStateComponent.type)
{
    override fun update(engine: EngineInterface, entities: EntityCollection)
    {
        val dt = engine.data.fixedDeltaTime
        for(entity in entities)
        {
            val transform = entity.getComponent(ParticleStateComponent.type)

            transform.xLast = transform.x
            transform.yLast = transform.y

            transform.xVel *= 1.0f - (FRICTION * dt)
            transform.yVel *= 1.0f - (FRICTION * dt)
            transform.yVel += GRAVITY * dt

            transform.x += transform.xVel * dt
            transform.y += transform.yVel * dt
        }
    }

    companion object
    {
        private const val GRAVITY = 0f
        private const val FRICTION = 0.3f
    }
}

class ParticleHealthSystem : LogicSystem(ParticleHealthComponent.type)
{
    override fun update(engine: EngineInterface, entities: EntityCollection)
    {
        val dt = engine.data.fixedDeltaTime
        for (entity in entities)
        {
            val health = entity.getComponent(ParticleHealthComponent.type)
            health.amount -= 0.05f * dt
            if(health.amount <= 0)
                entity.alive = false
        }
    }
}

class UserInputSystem : LogicSystem(ParticleStateComponent.type)
{
    override fun update(engine: EngineInterface, entities: EntityCollection)
    {
        val dt = engine.data.fixedDeltaTime

        if(engine.input.isPressed(Mouse.LEFT))
        {
            for(i in 0 until (10000*dt).toInt())
            {
                engine.entity.createWith(ParticleStateComponent.type, ParticleHealthComponent.type, ParticleColorComponent.type)
                    ?.let { entity ->
                        val transform = entity.getComponent(ParticleStateComponent.type)

                        val angle = Random.nextFloat() * 2 * PI
                        val vel = Random.nextFloat()

                        transform.x = engine.input.xMouse
                        transform.y = engine.input.yMouse
                        transform.xLast = engine.input.xMouse
                        transform.yLast = engine.input.yMouse
                        transform.xVel = sin(angle).toFloat() * 200f * vel
                        transform.yVel = cos(angle).toFloat() * 200f * vel
                    }
            }
        }

        if (engine.input.isPressed(Mouse.RIGHT))
        {
            for(entity in entities)
            {
                val transform = entity.getComponent(ParticleStateComponent.type)

                val xDelta = transform.x - engine.input.xMouse
                val yDelta = transform.y - engine.input.yMouse
                val length = sqrt(xDelta * xDelta + yDelta * yDelta)
                val xDir = xDelta / length
                val yDir = yDelta / length

                val invLength = 1.0f - (length / 2000f)

                transform.xVel -= xDir * invLength * 450 * dt
                transform.yVel -= yDir * invLength * 450 * dt
            }
        }
    }
}