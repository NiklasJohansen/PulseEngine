package no.njoh.pulseengine.modules.physics.bodies

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.modules.physics.ContactSolver
import no.njoh.pulseengine.modules.physics.BodyType.STATIC
import no.njoh.pulseengine.modules.physics.ContactResult
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.LENGTH
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.N_POINT_FIELDS
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.POINT_0
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.POINT_1
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.RESTING_MIN_VEL
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.RESTING_STEPS_BEFORE_SLEEP
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.STIFFNESS
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.X
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.X_ACC
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.X_LAST
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.Y
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.Y_ACC
import no.njoh.pulseengine.modules.physics.shapes.PolygonShape.Companion.Y_LAST
import kotlin.math.*

interface PolygonBody : PhysicsBody
{
    override val shape: PolygonShape

    override fun init(engine: PulseEngine)
    {
        if (this is Spatial)
        {
            shape.init(x, y, width, height, rotation, density)
        }
    }

    override fun beginStep(engine: PulseEngine, timeStep: Float, gravity: Float)
    {
        if (shape.isSleeping || bodyType == STATIC)
            return

        updateTransform(timeStep, gravity)
    }

    override fun iterateStep(engine: PulseEngine, iteration: Int, totalIterations: Int, worldWidth: Int, worldHeight: Int)
    {
        if (shape.isSleeping || bodyType == STATIC)
            return

        updateConstraints()
        updateCollisions(engine)
        updateWorldConstraint(worldWidth, worldHeight)

        // Last iteration
        if (iteration == totalIterations - 1)
        {
            shape.recalculateBoundingBox()
            shape.recalculateRotation()
            updateSleepState()
            onBodyUpdated()
        }
    }

    private fun updateTransform(timeStep: Float, gravity: Float)
    {
        val drag = 1f - drag
        val ts = timeStep * timeStep
        shape.forEachPoint { i ->
            val xNow = this[i + X]
            val yNow = this[i + Y]
            this[i + X] += (xNow - this[i + X_LAST]) * drag + this[i + X_ACC] * ts
            this[i + Y] += (yNow - this[i + Y_LAST]) * drag + this[i + Y_ACC] * ts
            this[i + X_LAST] = xNow
            this[i + Y_LAST] = yNow
            this[i + X_ACC] = 0f
            this[i + Y_ACC] = gravity
        }
    }

    private fun updateConstraints()
    {
        val points = shape.points
        shape.forEachStickConstraint { i ->
            val p0 = this[i + POINT_0].toInt() * N_POINT_FIELDS
            val p1 = this[i + POINT_1].toInt() * N_POINT_FIELDS
            val stiffness = this[i + STIFFNESS]
            val xDelta = points[p0 + X] - points[p1 + X]
            val yDelta = points[p0 + Y] - points[p1 + Y]
            val actualLength = sqrt(xDelta * xDelta + yDelta * yDelta)
            val targetLength = this[i + LENGTH]
            val deltaLength = (targetLength - actualLength) * 0.5f * stiffness
            val xChange = (xDelta / actualLength) * deltaLength
            val yChange = (yDelta / actualLength) * deltaLength

            // Move point positions
            points[p0 + X] += xChange
            points[p0 + Y] += yChange
            points[p1 + X] -= xChange
            points[p1 + Y] -= yChange
        }
    }

    private fun updateCenterAndRotation()
    {
        shape.recalculateBoundingBox()
        shape.recalculateRotation()
    }

    private fun updateSleepState()
    {
        val xVel = abs(shape.xCenter - shape.xCenterLast)
        val yVel = abs(shape.yCenter - shape.yCenterLast)
        if (xVel > RESTING_MIN_VEL || yVel > RESTING_MIN_VEL)
        {
            shape.stepsAtRest = 0
            shape.isSleeping = false
        }
        else if (shape.stepsAtRest >= RESTING_STEPS_BEFORE_SLEEP && !shape.isSleeping)
        {
            shape.isSleeping = true
            shape.forEachPoint { i ->
                this[i + X_LAST] = this[i + X]
                this[i + Y_LAST] = this[i + Y]
            }
        }
        else shape.stepsAtRest++
    }

    private fun updateCollisions(engine: PulseEngine)
    {
        val xMin = shape.xMin
        val xMax = shape.xMax
        val yMin = shape.yMin
        val yMax = shape.yMax

        engine.scene.forEachEntityNearby(shape.xCenter, shape.yCenter, xMax - xMin, yMax - yMin)
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

    fun updateWorldConstraint(worldWidth: Int, worldHeight: Int)
    {
        if (shape.xCenter < -worldWidth * 0.5f ||
            shape.xCenter > worldWidth * 0.5f ||
            shape.yCenter < -worldHeight * 0.5f ||
            shape.yCenter > worldHeight * 0.5f
        ) {
            bodyType = STATIC
        }
    }

    override fun render(engine: PulseEngine, surface: Surface)
    {
        if (shape.isSleeping)
            surface.setDrawColor(1f, 0.3f, 0.3f)
        else
            surface.setDrawColor(1f, 1f, 1f)

        val points = shape.points
        shape.forEachStickConstraint { i ->
            val p0 = this[i + POINT_0].toInt() * N_POINT_FIELDS
            val p1 = this[i + POINT_1].toInt() * N_POINT_FIELDS
            val x0 = points[p0 + X]
            val y0 = points[p0 + Y]
            val x1 = points[p1 + X]
            val y1 = points[p1 + Y]

            surface.drawQuad(x0 - 2f, y0 - 2f, 4f, 4f)
            surface.drawQuad(x1 - 2f, y1 - 2f, 4f, 4f)
            surface.drawLine(x0, y0, x1, y1)
        }

        shape.forEachPoint { i ->
            val x0 = points[i + X]
            val y0 = points[i + Y]
            surface.setDrawColor(1f, 1f, 1f)
            surface.drawQuad(x0 - 18f, y0 - 18f, 36f, 36f)
            surface.setDrawColor(0f, 0f, 0f)
            surface.drawText("${i / N_POINT_FIELDS}", x0, y0, xOrigin = 0.5f, yOrigin = 0.5f, fontSize = 50f)
        }
    }

    fun onBodyUpdated()
    {
        if (this is SceneEntity && this is Spatial)
        {
            x = shape.xCenter
            y = shape.yCenter
            rotation = shape.angle
            set(POSITION_UPDATED or ROTATION_UPDATED)
        }
    }

    override fun wakeUp()
    {
        shape.isSleeping = false
    }

    override fun hasOverlappingAABB(xMin: Float, yMin: Float, xMax: Float, yMax: Float): Boolean
    {
        val shape = shape
        return xMin < shape.xMax && xMax > shape.xMin && yMin < shape.yMax && yMax > shape.yMin
    }

    override fun getMass() = shape.mass

    override fun onCollision(engine: PulseEngine, otherBody: PhysicsBody, result: ContactResult) { }
}