package game.example

import engine.Engine
import engine.EngineInterface
import engine.abstraction.GameContext
import engine.data.Mouse
import engine.modules.entity.*
import engine.modules.graphics.BlendFunction
import game.cave.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

fun main()
{
    Engine().run(EntityGame())
}

class EntityGame : GameContext
{
    override fun init(engine: EngineInterface)
    {
        engine.config.targetFps = 120
        engine.entity.registerSystems(
            ParticleInteractionSystem(),
            ParticleMovementSystem(),
            ParticleRenderSystem(),
            HealthSystem()
        )
    }

    override fun update(engine: EngineInterface)
    {
        engine.window.title = "FPS: ${engine.data.currentFps} objects: ${engine.entity.count}"
    }

    override fun render(engine: EngineInterface)
    {
        engine.gfx.setBackgroundColor(0.1f, 0.1f, 0.1f)
    }

    override fun cleanUp(engine: EngineInterface) { }
}

// ------------------------------------------------- Components -------------------------------------------------

class TransformComponent : Component()
{
    var x: Float = 0f
    var y: Float = 0f
    var xLast: Float = 0f
    var yLast: Float = 0f

    companion object { val type = ComponentType(TransformComponent::class.java) }
}

class HealthComponent : Component()
{
    var amount: Float = 1f

    companion object { val type = ComponentType(HealthComponent::class.java) }
}

class ColorComponent : Component()
{
    val color: Color = staticColor

    companion object
    {
        private val staticColor: Color = Color(1f, 0.4f, 0.1f)
        val type = ComponentType(ColorComponent::class.java)
    }
}

// ------------------------------------------------- Systems -------------------------------------------------

class ParticleMovementSystem : LogicSystem(TransformComponent.type)
{
    override fun update(engine: EngineInterface, entities: EntityCollection)
    {
        for(entity in entities)
        {
            val transform = entity.getComponent(TransformComponent.type)

            val xDelta = transform.x - transform.xLast
            val yDelta = transform.y - transform.yLast

            transform.xLast = transform.x
            transform.yLast = transform.y
            transform.x += xDelta * FRICTION
            transform.y += yDelta * FRICTION + GRAVITY
        }
    }

    companion object
    {
        private const val GRAVITY = 0.0f
        private const val FRICTION = 0.995f
    }
}

class ParticleInteractionSystem : LogicSystem(TransformComponent.type)
{
    override fun update(engine: EngineInterface, entities: EntityCollection)
    {
        if(engine.input.isPressed(Mouse.LEFT))
        {
            for(i in 0 until 100)
            {
                engine.entity.createWith(TransformComponent.type, HealthComponent.type, ColorComponent.type)
                    ?.let { entity ->
                        val transform = entity.getComponent(TransformComponent.type)

                        val angle = Random.nextFloat() * 2 * PI
                        val vel = Random.nextFloat()

                        transform.xLast = engine.input.xMouse
                        transform.yLast = engine.input.yMouse
                        transform.x = transform.xLast + sin(angle).toFloat() * 5f * vel
                        transform.y = transform.yLast + cos(angle).toFloat() * 5f * vel
                }
            }
        }

        if (engine.input.isPressed(Mouse.RIGHT))
        {
            for(entity in entities)
            {
                val transform = entity.getComponent(TransformComponent.type)

                val xDelta = transform.x - engine.input.xMouse
                val yDelta = transform.y - engine.input.yMouse
                val length = sqrt(xDelta * xDelta + yDelta * yDelta)
                val xDir = xDelta / length
                val yDir = yDelta / length

                val dx = transform.x - transform.xLast
                val dy = transform.y - transform.yLast

                val invLength = 1.0f - (length / 2000f)

                transform.xLast = transform.x - dx + (xDir * invLength * 0.1f)
                transform.yLast = transform.y - dy + (yDir * invLength * 0.1f)
            }
        }
    }
}

class ParticleRenderSystem : RenderSystem(TransformComponent.type, HealthComponent.type, ColorComponent.type)
{
    override fun render(engine: EngineInterface, entities: EntityCollection)
    {
        engine.gfx.setBlendFunction(BlendFunction.ADDITIVE)
        engine.gfx.setLineWidth(1f)
        engine.gfx.drawLines { draw ->
            for (entity in entities)
            {
                val transform = entity.getComponent(TransformComponent.type)
                val health = entity.getComponent(HealthComponent.type)
                val color = entity.getComponent(ColorComponent.type)

                val fade = if(health.amount < 0.2f) health.amount / 0.2f else 1.0f

                draw.color(color.color.red, color.color.green, color.color.blue, 0.5f * fade)
                draw.line(transform.x, transform.y, transform.xLast, transform.yLast)
            }
        }
    }
}

class HealthSystem : LogicSystem(HealthComponent.type)
{
    override fun update(engine: EngineInterface, entities: EntityCollection)
    {
        for (entity in entities)
        {
            val health = entity.getComponent(HealthComponent.type)
            health.amount -= 0.0005f
            if(health.amount <= 0)
                entity.alive = false
        }
    }
}



