package no.njoh.pulseengine.modules.physics.bodies

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.modules.physics.ContactSolver
import no.njoh.pulseengine.modules.physics.BodyType.*
import no.njoh.pulseengine.modules.physics.ContactResult
import no.njoh.pulseengine.modules.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.physics.shapes.CircleShape.Companion.RESTING_MIN_VEL
import no.njoh.pulseengine.modules.physics.shapes.CircleShape.Companion.RESTING_STEPS_BEFORE_SLEEP
import kotlin.math.*

interface CircleBody : PhysicsBody
{
    override val shape: CircleShape

    override fun init(engine: PulseEngine)
    {
        if (this is Spatial)
        {
            shape.init(x, y, max(width, height) * 0.5f, rotation, density)
        }
    }

    override fun beginStep(engine: PulseEngine, timeStep: Float, gravity: Float)
    {
        if (shape.isSleeping || bodyType == STATIC)
            return

        val angularDrag = 1f - drag * 0.1f
        val drag = 1f - drag
        val xNow = shape.x
        val yNow = shape.y
        val xVel = (xNow - shape.xLast) * drag + shape.xAcc * timeStep * timeStep
        val yVel = (yNow - shape.yLast) * drag + shape.yAcc * timeStep * timeStep
        val rotNow = shape.rot
        val rotVel = (rotNow - shape.rotLast) * angularDrag

        shape.xLast = xNow
        shape.yLast = yNow
        shape.x = xNow + xVel
        shape.y = yNow + yVel
        shape.xAcc = 0f
        shape.yAcc = gravity
        shape.rotLast = rotNow
        shape.rot += rotVel
        shape.lastRotVel = rotVel
    }

    override fun iterateStep(engine: PulseEngine, iteration: Int, totalIterations: Int, worldWidth: Int, worldHeight: Int)
    {
        if (shape.isSleeping || bodyType == STATIC)
            return

        // Handle world size constraint
        if (shape.x < worldWidth * -0.5f || shape.x > worldWidth * 0.5f || shape.y < worldHeight * -0.5f || shape.y > worldHeight * 0.5f)
            bodyType = STATIC

        // Handle collision
        updateCollisions(engine)

        // Update last angular velocity
        shape.lastRotVel = shape.rot - shape.rotLast

        // Last iteration
        if (iteration == totalIterations - 1)
        {
            updateSleepState()
            onBodyUpdated()
        }
    }

    private fun updateCollisions(engine: PulseEngine)
    {
        val radius = shape.radius
        val xMin = shape.x - radius
        val yMin = shape.y - radius
        val xMax = shape.x + radius
        val yMax = shape.y + radius

        engine.scene.forEachEntityNearby(shape.x, shape.y, radius * 2f, radius * 2f)
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
            shape.stepsAtRest = 0
            shape.isSleeping = true
        }
        else shape.stepsAtRest++
    }

    override fun wakeUp()
    {
        shape.isSleeping = false
    }

    override fun render(engine: PulseEngine, surface: Surface2D)
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

        val xEnd = xCenter + cos(-shape.rot) * radius
        val yEnd = yCenter + sin(-shape.rot) * radius
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

        surface.setDrawColor(1f, 0f, 0f)
        surface.drawText(shape.stepsAtRest.toString(), xCenter, yCenter, fontSize = 50f)
    }

    fun onBodyUpdated()
    {
        if (this is SceneEntity && this is Spatial)
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

    override fun getMass() = shape.mass

    override fun onCollision(engine: PulseEngine, otherBody: PhysicsBody, result: ContactResult) { }
}