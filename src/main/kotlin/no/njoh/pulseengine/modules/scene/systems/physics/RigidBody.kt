package no.njoh.pulseengine.modules.scene.systems.physics

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.SpatialGrid
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape
import kotlin.math.sqrt

interface RigidBody
{
    val shape: Shape
    var bodyType: BodyType
    var friction: Float
    var drag: Float
    var mass: Float

    fun init()
    {
        if (this is SceneEntity)
        {
            shape.build(x, y, width, height, rotation)
            onBodyUpdated(shape.xCenter, shape.yCenter, shape.xCenterLast, shape.yCenterLast, rotation)
        }
    }

    fun updateTransform(gravity: Float)
    {
        val drag = 1f - drag
        shape.forEachPoint { i ->
            val xNow = this[i + Shape.X]
            val yNow = this[i + Shape.Y]
            this[i + Shape.X] += (xNow - this[i + Shape.X_OLD]) * drag + this[i + Shape.X_ACC]
            this[i + Shape.Y] += (yNow - this[i + Shape.Y_OLD]) * drag + this[i + Shape.Y_ACC]
            this[i + Shape.X_OLD] = xNow
            this[i + Shape.Y_OLD] = yNow
            this[i + Shape.X_ACC] = 0f
            this[i + Shape.Y_ACC] = gravity
        }
    }

    fun updateConstraints()
    {
        val points = shape.points
        shape.forEachConstraint { i ->
            val p0 = this[i + Shape.POINT_0].toInt() * Shape.N_POINT_FIELDS
            val p1 = this[i + Shape.POINT_1].toInt() * Shape.N_POINT_FIELDS
            val stiffness = this[i + Shape.STIFFNESS]
            val xDelta = points[p0 + Shape.X] - points[p1 + Shape.X]
            val yDelta = points[p0 + Shape.Y] - points[p1 + Shape.Y]
            val actualLength = sqrt(xDelta * xDelta + yDelta * yDelta)
            val targetLength = this[i + Shape.LENGTH]
            val deltaLength = (targetLength - actualLength) * 0.5f * stiffness
            val xChange = (xDelta / actualLength) * deltaLength
            val yChange = (yDelta / actualLength) * deltaLength

            // Move point positions
            points[p0 + Shape.X] += xChange
            points[p0 + Shape.Y] += yChange
            points[p1 + Shape.X] -= xChange
            points[p1 + Shape.Y] -= yChange
        }
    }

    fun updateCenterAndRotation()
    {
        shape.recalculateBoundingBox()
        shape.recalculateRotation()
        onBodyUpdated(shape.xCenter, shape.yCenter, shape.xCenterLast, shape.yCenterLast, shape.angle)
    }

    fun updateSleepState()
    {
        if (shape.xCenter.toInt() != shape.xCenterLast.toInt() || shape.yCenter.toInt() != shape.yCenterLast.toInt())
            shape.sleepCount = 0

        if (!shape.isSleeping && shape.sleepCount > 60)
        {
            // Kill momentum
            shape.forEachPoint { i ->
                this[i + Shape.X_OLD] = this[i + Shape.X]
                this[i + Shape.Y_OLD] = this[i + Shape.Y]
            }
        }

        shape.isSleeping = (shape.sleepCount > 60)
        shape.sleepCount++
    }

    fun updateCollisions(engine: PulseEngine, spatialGrid: SpatialGrid)
    {
        val xMin = shape.xMin
        val xMax = shape.xMax
        val yMin = shape.yMin
        val yMax = shape.yMax

        spatialGrid.query(shape.xCenter, shape.yCenter, xMax - xMin, yMax - yMin)
        {
            if (it is RigidBody && it.shape !== shape)
            {
                if (xMin < it.shape.xMax && xMax > it.shape.xMin && yMin < it.shape.yMax && yMax > it.shape.yMin)
                {
                    BodyInteraction.detectAndResolve(this, it)?.let { result ->
                        onCollision(engine, it, result)
                        if (this is SceneEntity)
                            it.onCollision(engine, this, result)
                    }
                }
            }
        }
    }

    fun updateWorldConstraint(worldWidth: Int, worldHeight: Int)
    {
        if (shape.xCenter < -worldWidth / 2f ||
            shape.xCenter > worldWidth / 2f ||
            shape.yCenter < -worldHeight / 2f ||
            shape.yCenter > worldHeight / 2f
        ) {
            bodyType = BodyType.STATIC
        }
    }

    fun drawConstraints(surface: Surface2D)
    {
        if (shape.isSleeping)
            surface.setDrawColor(1f, 0.3f, 0.3f)
        else
            surface.setDrawColor(1f, 1f, 1f)

        val points = shape.points
        shape.forEachConstraint { i ->
            val p0 = this[i + Shape.POINT_0].toInt() * Shape.N_POINT_FIELDS
            val p1 = this[i + Shape.POINT_1].toInt() * Shape.N_POINT_FIELDS
            val x0 = points[p0 + Shape.X]
            val y0 = points[p0 + Shape.Y]
            val x1 = points[p1 + Shape.X]
            val y1 = points[p1 + Shape.Y]

            surface.drawQuad(x0 - 5f, y0 - 5f, 10f, 10f)
            surface.drawQuad(x1 - 5f, y1 - 5f, 10f, 10f)
            surface.drawLine(x0, y0, x1, y1)
        }
    }

    fun onBodyUpdated(xCenter: Float, yCenter: Float, xCenterLast: Float, yCenterLast: Float, angle: Float)
    fun onCollision(engine: PulseEngine, otherEntity: SceneEntity, result: CollisionResult) { }
}