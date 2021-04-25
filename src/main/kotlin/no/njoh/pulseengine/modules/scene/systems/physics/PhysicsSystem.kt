package no.njoh.pulseengine.modules.scene.systems.physics

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Key
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.data.SceneState.RUNNING
import no.njoh.pulseengine.modules.scene.SceneManager
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.Body
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.RigidBody
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape
import no.njoh.pulseengine.util.forEachFast
import no.njoh.pulseengine.util.forEachReversed
import no.njoh.pulseengine.widgets.sceneEditor.ValueRange

class PhysicsSystem : SceneSystem()
{
    var gravity = 0.6f
    var drawShapes = false
    var mouseInteraction = false

    @ValueRange(0f, 100_000_000f)
    var worldWidth = 100_000

    @ValueRange(0f, 100_000_000f)
    var worldHeight = 100_000

    @ValueRange(0f, 50f)
    var physicsIterations = 2

    @JsonIgnore
    private var pickedShape: Shape? = null

    @JsonIgnore
    private var pickedPointIndex = 0

    @JsonIgnore
    private var reversedUpdateOrder = false

    override fun onStart(engine: PulseEngine)
    {
        engine.scene.forEachBody { it.init() }
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (engine.scene.state != RUNNING)
            return

        val spatialGrid = engine.scene.activeScene.spatialGrid
        if (reversedUpdateOrder)
            engine.scene.forEachBodyReversed { it.update(engine, spatialGrid, gravity, physicsIterations, worldWidth, worldHeight) }
        else
            engine.scene.forEachBody { it.update(engine, spatialGrid, gravity, physicsIterations, worldWidth, worldHeight) }

        reversedUpdateOrder = !reversedUpdateOrder

        if (mouseInteraction)
            pickBody(engine)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (engine.input.wasClicked(Key.COMMA))
            drawShapes = !drawShapes
    }

    override fun onRender(engine: PulseEngine)
    {
        if (!drawShapes)
            return

        val surface = engine.gfx.mainSurface
        engine.scene.forEachBody { it.render(surface) }
    }

    private fun pickBody(engine: PulseEngine)
    {
        if (!engine.input.isPressed(Mouse.LEFT))
        {
            pickedShape = null
            return
        }
        else if (pickedShape != null)
        {
            pickedShape!!.points[pickedPointIndex + Shape.X] = engine.input.xWorldMouse
            pickedShape!!.points[pickedPointIndex + Shape.Y] = engine.input.yWorldMouse
            pickedShape!!.isSleeping = false
            return
        }

        val xMouse = engine.input.xWorldMouse
        val yMouse = engine.input.yWorldMouse
        val minDist = 40f * 40f

        engine.scene.forEachNearbyEntityOfType<RigidBody>(xMouse, yMouse, 500f, 500f)
        {
            it.shape.forEachPoint { i ->
                val xPoint = this[i + Shape.X]
                val yPoint = this[i + Shape.Y]
                val xDelta = xPoint - xMouse
                val yDelta = yPoint - yMouse
                val dist = xDelta * xDelta + yDelta * yDelta
                if (dist < minDist)
                {
                    pickedPointIndex = i
                    pickedShape = it.shape
                    this[i + Shape.X] = xMouse
                    this[i + Shape.Y] = yMouse
                    return
                }
            }
        }
    }

    private inline fun SceneManager.forEachBody(block: (body: Body) -> Unit)
    {
        activeScene.entities.forEachFast { entities ->
            if (entities.isNotEmpty() && entities[0] is Body)
                entities.forEachFast { block(it as Body) }
        }
    }

    private inline fun SceneManager.forEachBodyReversed(block: (body: Body) -> Unit)
    {
        activeScene.entities.forEachReversed { entities ->
            if (entities.isNotEmpty() && entities[0] is Body)
                entities.forEachReversed { block(it as Body) }
        }
    }
}