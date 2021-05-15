package no.njoh.pulseengine.modules.scene.systems.physics.bodies

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.entities.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.modules.scene.entities.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.modules.scene.systems.physics.ContactSolver
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType
import no.njoh.pulseengine.modules.scene.systems.physics.ContactResult
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.CircleShape.Companion.RESTING_MIN_VEL
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.CircleShape.Companion.RESTING_STEPS_BEFORE_SLEEP
import org.joml.Vector2f
import kotlin.math.*

interface CircleBody : PhysicsBody
{
    val shape: CircleShape

    override fun init()
    {
        if (this is SceneEntity)
        {
            shape.x = x
            shape.y = y
            shape.xLast = x
            shape.yLast = y
            shape.radius = max(this.width, this.height) * 0.5f
            shape.rot = rotation
            shape.rotLast = rotation
            onBodyUpdated(shape)
        }
    }

    override fun beginStep(timeStep: Float, gravity: Float)
    {
        if (shape.isSleeping)
            return

        val drag = 1f - drag
        val xNow = shape.x
        val yNow = shape.y
        val xVel = (xNow - shape.xLast) * drag + shape.xAcc * timeStep * timeStep
        val yVel = (yNow - shape.yLast) * drag + shape.yAcc * timeStep * timeStep
        val xCenter = xNow + xVel
        val yCenter = yNow + yVel
        val rotNow = shape.rot
        val rotVel = (rotNow - shape.rotLast) * drag

        shape.xLast = xNow
        shape.yLast = yNow
        shape.x = xCenter
        shape.y = yCenter
        shape.xAcc = 0f
        shape.yAcc = gravity
        shape.rotLast = rotNow
        shape.rot += rotVel
        shape.lastRotVel = rotVel
    }

    override fun iterateStep(iteration: Int, totalIterations: Int, engine: PulseEngine, worldWidth: Int, worldHeight: Int)
    {
        if (shape.isSleeping)
            return

        // Handle world size constraint
        if (shape.x < worldWidth * -0.5f || shape.x > worldWidth * 0.5f || shape.y < worldHeight * -0.5f || shape.y > worldHeight * 0.5f)
            bodyType = BodyType.STATIC

        // Handle collision
        updateCollisions(engine)

        // Update last angular velocity
        shape.lastRotVel = shape.rot - shape.rotLast

        // Last iteration
        if (iteration == totalIterations - 1)
        {
            updateSleepState()
            onBodyUpdated(shape)
        }
    }

    private fun updateCollisions(engine: PulseEngine)
    {
        val radius = shape.radius
        val xMin = shape.x - radius
        val yMin = shape.y - radius
        val xMax = shape.x + radius
        val yMax = shape.y + radius

        engine.scene.forEachNearbyEntity(shape.x, shape.y, radius * 2f, radius * 2f)
        {
            if (it !== this &&
                it is PhysicsBody &&
                it.layerMask and this.collisionMask != 0 &&
                it.hasOverlappingAABB(xMin, yMin, xMax, yMax)
            ) {
                ContactSolver.solve(this, it)?.let { result ->
                    onCollision(engine, it, result)
                    it.onCollision(engine, this, result)
                    it.wakeUp()
                }
            }
        }
    }

    private fun updateSleepState()
    {
        val xVel = abs(shape.x - shape.xLast)
        val yVel = abs(shape.y - shape.yLast)
        val rVel = abs(shape.lastRotVel * shape.radius)
        if (xVel > RESTING_MIN_VEL || yVel > RESTING_MIN_VEL || rVel > RESTING_MIN_VEL)
        {
            shape.stepsAtRest = 0
            shape.isSleeping = false
        }
        else if (shape.stepsAtRest >= RESTING_STEPS_BEFORE_SLEEP && !shape.isSleeping)
        {
            shape.xLast = shape.x
            shape.yLast = shape.y
            shape.isSleeping = true
        }
        else shape.stepsAtRest++
    }

    override fun wakeUp()
    {
        shape.isSleeping = false
    }

    override fun render(surface: Surface2D)
    {
        val dotSize = 5f
        val radius = shape.radius
        val xCenter = shape.x
        val yCenter = shape.y

        surface.setDrawColor(1f, 0f, 0f)
        surface.drawLine(xCenter, yCenter, shape.xLast, shape.yLast)
        surface.drawQuad(xCenter - 0.5f * dotSize, yCenter - 0.5f * dotSize, dotSize, dotSize)

        if (shape.isSleeping)
            surface.setDrawColor(1f, 0.3f, 0.3f)
        else
            surface.setDrawColor(1f, 1f, 1f)

        val xEnd = xCenter + cos(shape.rot) * radius
        val yEnd = yCenter + sin(shape.rot) * radius
        surface.drawLine(xCenter, yCenter, xEnd, yEnd)

        val nPoints = 30
        var xLast = xCenter + radius
        var yLast = yCenter
        for (i in 1 until nPoints + 1)
        {
            val deg = (i.toFloat() / nPoints) * PI.toFloat() * 2f
            val x = xCenter + cos(deg) * radius
            val y = yCenter + sin(deg) * radius
            surface.drawLine(x, y, xLast, yLast)
            xLast = x
            yLast = y
        }
    }

    fun onBodyUpdated(shape: CircleShape)
    {
        if (this is SceneEntity)
        {
            x = shape.x
            y = shape.y
            rotation = shape.rot / PI.toFloat() * 180f
            set(POSITION_UPDATED or ROTATION_UPDATED)
        }
    }

    override fun hasOverlappingAABB(xMin: Float, yMin: Float, xMax: Float, yMax: Float): Boolean
    {
        val circle = shape
        val radius = circle.radius * 2
        return xMin < circle.x + radius && xMax > circle.x - radius && yMin < circle.y + radius && yMax > circle.y - radius
    }

    override fun setPoint(index: Int, x: Float, y: Float)
    {
        shape.x = x
        shape.y = y
    }

    override fun getPoint(index: Int): Vector2f? =
        CircleShape.reusableVector.set(shape.x, shape.y)

    override fun getPointCount() = 1

    override fun onCollision(engine: PulseEngine, otherBody: PhysicsBody, result: ContactResult) { }
}