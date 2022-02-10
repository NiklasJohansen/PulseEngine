package no.njoh.pulseengine.core.scene.systems.physics

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Mouse
import no.njoh.pulseengine.core.scene.SceneState.RUNNING
import no.njoh.pulseengine.core.scene.SceneManager
import no.njoh.pulseengine.core.scene.systems.SceneSystem
import no.njoh.pulseengine.core.scene.systems.physics.bodies.PhysicsBody
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.widgets.editor.Name
import no.njoh.pulseengine.widgets.editor.Property

@Name("2D Physics")
class PhysicsSystem : SceneSystem()
{
    @Property(order = 1)
    var gravity = 1500f

    @Property(order = 2, min = 0f, max = 50f)
    var physicsIterations = 4

    @Property(order = 3, min = 0f, max = 100_000_000f)
    var worldWidth = 100_000

    @Property(order = 4, min = 0f, max = 100_000_000f)
    var worldHeight = 100_000

    @Property(order = 5)
    var mousePicking = false

    @Property(order = 6)
    var drawShapes = false

    @JsonIgnore
    private var pickedBody: PhysicsBody? = null

    @JsonIgnore
    private var pickedPointIndex = 0

    override fun onStart(engine: PulseEngine)
    {
        engine.scene.forEachPhysicsEntity { it.init(engine) }
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (engine.scene.state != RUNNING)
            return

        engine.scene.forEachPhysicsEntity { it.beginStep(engine, engine.data.fixedDeltaTime, gravity) }

        val totalIterations = physicsIterations
        for (i in 0 until totalIterations)
            engine.scene.forEachPhysicsEntity { it.iterateStep(engine, i, totalIterations, worldWidth, worldHeight) }

        if (mousePicking)
            pickBody(engine)
    }

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(engine: PulseEngine)
    {
        if (!drawShapes)
            return

        val surface = engine.gfx.mainSurface
        engine.scene.forEachPhysicsEntity { it.render(engine, surface) }
    }

    private fun pickBody(engine: PulseEngine)
    {
        if (!engine.input.isPressed(Mouse.LEFT))
        {
            pickedBody = null
            return
        }
        else if (pickedBody != null)
        {
            pickedBody!!.shape.setPoint(pickedPointIndex, engine.input.xWorldMouse, engine.input.yWorldMouse)
            pickedBody!!.wakeUp()
            return
        }

        val minDist = 40f * 40f
        val xMouse = engine.input.xWorldMouse
        val yMouse = engine.input.yWorldMouse
        engine.scene.forEachNearbyEntityOfType<PhysicsBody>(xMouse, yMouse, 500f, 500f)
        {
            for (i in 0 until it.shape.getPointCount())
            {
                it.shape.getPoint(i)?.let { point ->
                    val xDelta = point.x - xMouse
                    val yDelta = point.y - yMouse
                    val dist = xDelta * xDelta + yDelta * yDelta
                    if (dist < minDist)
                    {
                        pickedPointIndex = i
                        pickedBody = it
                        return
                    }
                }
            }
        }
    }

    private inline fun SceneManager.forEachPhysicsEntity(block: (body: PhysicsEntity) -> Unit)
    {
        activeScene.entities.forEachFast { entities ->
            if (entities.isNotEmpty() && entities[0] is PhysicsEntity)
                entities.forEachFast { block(it as PhysicsEntity) }
        }
    }
}