package no.njoh.pulseengine.modules.scene.systems.physics.bodies

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.entities.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.modules.scene.entities.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.modules.scene.systems.physics.ContactSolver
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType.STATIC
import no.njoh.pulseengine.modules.scene.systems.physics.ContactResult
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.LENGTH
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.N_POINT_FIELDS
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.POINT_0
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.POINT_1
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.RESTING_MIN_VEL
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.RESTING_STEPS_BEFORE_SLEEP
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.STIFFNESS
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.X
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.X_ACC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.X_LAST
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.Y
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.Y_ACC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.Y_LAST
import org.joml.Vector2f
import kotlin.math.*

interface PolygonBody : PhysicsBody
{
    val shape: PolygonShape

    override fun init()
    {
        if (this is SceneEntity)
        {
            shape.build(x, y, width, height, rotation, density)
            onBodyUpdated(shape.xCenter, shape.yCenter, shape.xCenterLast, shape.yCenterLast, rotation)
        }
    }

    override fun beginStep(timeStep: Float, gravity: Float)
    {
        if (shape.isSleeping)
            return

        updateTransform(timeStep, gravity)
    }

    override fun iterateStep(iteration: Int, totalIterations: Int, engine: PulseEngine, worldWidth: Int, worldHeight: Int)
    {
        if (shape.isSleeping)
            return

        updateConstraints()
        updateCollisions(engine)
        updateWorldConstraint(worldWidth, worldHeight)

        // Last iteration
        if (iteration == totalIterations - 1)
        {
            updateCenterAndRotation()
            updateSleepState()
            onBodyUpdated(shape.xCenter, shape.yCenter, shape.xCenterLast, shape.yCenterLast, shape.angle)
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

        engine.scene.forEachNearbyEntity(shape.xCenter, shape.yCenter, xMax - xMin, yMax - yMin)
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

    override fun render(surface: Surface2D)
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
            surface.setDrawColor(1f, 0f, 0f)
            surface.drawText("${i / N_POINT_FIELDS}", x0 - 10, y0 - 10, fontSize = 50f)
        }
    }

    fun onBodyUpdated(xCenter: Float, yCenter: Float, xCenterLast: Float, yCenterLast: Float, angle: Float)
    {
        if (this is SceneEntity)
        {
            x = xCenter
            y = yCenter
            rotation = angle
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

    override fun setPoint(index: Int, x: Float, y: Float)
    {
        if (index >= 0 && index * N_POINT_FIELDS < shape.points.size)
        {
            shape.points[index * N_POINT_FIELDS + X] = x
            shape.points[index * N_POINT_FIELDS + Y] = y
        }
    }

    override fun getPoint(index: Int): Vector2f? =
        if (index < 0 || index * N_POINT_FIELDS >= shape.points.size) null
        else PolygonShape.reusableVector.set(shape.points[index * N_POINT_FIELDS + X], shape.points[index * N_POINT_FIELDS + Y])

    override fun getPointCount() = shape.points.size / N_POINT_FIELDS

    override fun onCollision(engine: PulseEngine, otherBody: PhysicsBody, result: ContactResult) { }
}