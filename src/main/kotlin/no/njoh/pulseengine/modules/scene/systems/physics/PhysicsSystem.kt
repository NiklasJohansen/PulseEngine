package no.njoh.pulseengine.modules.scene.systems.physics

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Key
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.data.SceneState.RUNNING
import no.njoh.pulseengine.modules.scene.SceneManager
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType.DYNAMIC
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.PhysicsBody
import no.njoh.pulseengine.util.forEachFast
import no.njoh.pulseengine.widgets.sceneEditor.ValueRange

class PhysicsSystem : SceneSystem()
{
    var gravity = 1500f
    var drawShapes = false
    var mouseInteraction = false

    @ValueRange(0f, 100_000_000f)
    var worldWidth = 100_000

    @ValueRange(0f, 100_000_000f)
    var worldHeight = 100_000

    @ValueRange(0f, 50f)
    var physicsIterations = 4

    @JsonIgnore
    private var pickedBody: PhysicsBody? = null

    @JsonIgnore
    private var pickedPointIndex = 0

    override fun onStart(engine: PulseEngine)
    {
        engine.scene.forEachBody { it.init() }
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (engine.scene.state != RUNNING)
            return

        engine.scene.forEachDynamicBody { it.beginStep(engine.data.fixedDeltaTime, gravity) }

        val totalIterations = physicsIterations
        for (i in 0 until totalIterations)
            engine.scene.forEachDynamicBody { it.iterateStep(i, totalIterations, engine, worldWidth, worldHeight) }

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
            pickedBody = null
            return
        }
        else if (pickedBody != null)
        {
            pickedBody!!.setPoint(pickedPointIndex, engine.input.xWorldMouse, engine.input.yWorldMouse)
            return
        }

        val minDist = 40f * 40f
        val xMouse = engine.input.xWorldMouse
        val yMouse = engine.input.yWorldMouse
        engine.scene.forEachNearbyEntityOfType<PhysicsBody>(xMouse, yMouse, 500f, 500f)
        {
            for (i in 0 until it.getPointCount())
            {
                it.getPoint(i)?.let { point ->
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

    private inline fun SceneManager.forEachBody(block: (body: PhysicsBody) -> Unit)
    {
        activeScene.entities.forEachFast { entities ->
            if (entities.isNotEmpty() && entities[0] is PhysicsBody)
                entities.forEachFast { block(it as PhysicsBody) }
        }
    }

    private inline fun SceneManager.forEachDynamicBody(block: (body: PhysicsBody) -> Unit)
    {
        activeScene.entities.forEachFast { entities ->
            if (entities.isNotEmpty() && entities[0] is PhysicsBody)
                entities.forEachFast {
                    it as PhysicsBody
                    if (it.bodyType == DYNAMIC)
                        block(it)
                }
        }
    }
}