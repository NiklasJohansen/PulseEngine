package no.njoh.pulseengine.modules.scene.systems.physics

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Key
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.data.SceneState.RUNNING
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
        scene.forEachBody { it.init() }
    }

    override fun onFixedUpdate(scene: Scene, engine: PulseEngine)
    {
        if (engine.scene.state != RUNNING)
            return

        val spatialGrid = scene.spatialGrid
        scene.forEachBody { body ->
            body.update(engine, spatialGrid, gravity, physicsIterations, worldWidth, worldHeight)
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
        scene.forEachBody { it.render(surface) }
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
            return
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

    private inline fun Scene.forEachBody(block: (body: Body) -> Unit)
    {
        entityCollections.forEachFast { entities ->
            if (entities.isNotEmpty() && entities[0] is Body)
                entities.forEachFast { block(it as Body) }
        }
    }
}