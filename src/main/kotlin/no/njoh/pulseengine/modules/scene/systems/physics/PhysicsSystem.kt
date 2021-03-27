package no.njoh.pulseengine.modules.scene.systems.physics

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Key
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.modules.Input
import no.njoh.pulseengine.modules.scene.Scene
import no.njoh.pulseengine.modules.scene.SpatialGrid
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape
import no.njoh.pulseengine.util.forEachFast
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

    override fun onStart(scene: Scene, engine: PulseEngine)
    {
        scene.forEachRigidBody { it.init() }
    }

    override fun onFixedUpdate(scene: Scene, engine: PulseEngine)
    {
        val spatialGrid = scene.spatialGrid
        scene.forEachRigidBody { body ->
            if (body.bodyType == BodyType.DYNAMIC && !body.shape.isSleeping)
            {
                body.updateTransform(gravity)

                for (i in 0 until physicsIterations)
                {
                    body.updateConstraints()
                    body.updateCollisions(engine, spatialGrid)
                }

                body.updateCenterAndRotation()
                body.updateSleepState()
                body.updateWorldConstraint(worldWidth, worldHeight)
            }
        }

        if (mouseInteraction)
            pickBody(spatialGrid, engine.input)
    }

    override fun onUpdate(scene: Scene, engine: PulseEngine)
    {
        if (engine.input.wasClicked(Key.COMMA))
            drawShapes = !drawShapes
    }

    override fun onRender(scene: Scene, engine: PulseEngine)
    {
        if (!drawShapes)
            return

        val surface = engine.gfx.mainSurface
        scene.forEachRigidBody { it.drawConstraints(surface) }
    }

    private fun pickBody(spatialGrid: SpatialGrid, input: Input)
    {
        if (!input.isPressed(Mouse.LEFT))
        {
            pickedShape = null
            return
        }
        else if (pickedShape != null)
        {
            pickedShape!!.points[pickedPointIndex + Shape.X] = input.xWorldMouse
            pickedShape!!.points[pickedPointIndex + Shape.Y] = input.yWorldMouse
            pickedShape!!.isSleeping = false
        }

        val xMouse = input.xWorldMouse
        val yMouse = input.yWorldMouse
        val minDist = 40f * 40f

        spatialGrid.queryType<RigidBody>(xMouse, yMouse, 500f, 500f)
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

    private inline fun Scene.forEachRigidBody(block: (body: RigidBody) -> Unit)
    {
        entityCollections.forEachFast { entities ->
            if (entities.isNotEmpty() && entities[0] is RigidBody)
                entities.forEachFast { block(it as RigidBody) }
        }
    }
}