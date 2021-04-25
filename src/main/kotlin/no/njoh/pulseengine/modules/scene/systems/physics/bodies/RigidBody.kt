package no.njoh.pulseengine.modules.scene.systems.physics.bodies

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.SpatialGrid
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.physics.BodyInteraction
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType.STATIC
import no.njoh.pulseengine.modules.scene.systems.physics.CollisionResult
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.LENGTH
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.N_POINT_FIELDS
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.POINT_0
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.POINT_1
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.STIFFNESS
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.X
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.X_ACC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.X_LAST
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.Y
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.Y_ACC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.Y_LAST
import kotlin.math.*

interface RigidBody : Body
{
    val shape: Shape

    override fun init()
    {
        if (this is SceneEntity)
        {
            shape.build(x, y, width, height, rotation)
            onBodyUpdated(shape.xCenter, shape.yCenter, shape.xCenterLast, shape.yCenterLast, rotation)
        }
    }

    override fun update(engine: PulseEngine, spatialGrid: SpatialGrid, gravity: Float, physicsIterations: Int, worldWidth: Int, worldHeight: Int)
    {
        if (bodyType != BodyType.DYNAMIC || shape.isSleeping)
            return

        updateTransform(gravity)

        for (i in 0 until physicsIterations)
        {
            updateConstraints()
            updateCollisions(engine, spatialGrid)
        }

        updateCenterAndRotation()
        updateSleepState()
        updateWorldConstraint(worldWidth, worldHeight)
    }

    private fun updateTransform(gravity: Float)
    {
        val drag = 1f - drag
        shape.forEachPoint { i ->
            val xNow = this[i + X]
            val yNow = this[i + Y]
            this[i + X] += (xNow - this[i + X_LAST]) * drag + this[i + X_ACC]
            this[i + Y] += (yNow - this[i + Y_LAST]) * drag + this[i + Y_ACC]
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
        onBodyUpdated(shape.xCenter, shape.yCenter, shape.xCenterLast, shape.yCenterLast, shape.angle)
    }

    private fun updateSleepState()
    {
        if (shape.xCenter.toInt() != shape.xCenterLast.toInt() || shape.yCenter.toInt() != shape.yCenterLast.toInt())
            shape.sleepCount = 0

        if (!shape.isSleeping && shape.sleepCount > 60)
        {
            // Kill momentum
            shape.forEachPoint { i ->
                this[i + X_LAST] = this[i + X]
                this[i + Y_LAST] = this[i + Y]
            }
        }

        shape.isSleeping = (shape.sleepCount > 60)
        shape.sleepCount++
    }

    private fun updateCollisions(engine: PulseEngine, spatialGrid: SpatialGrid)
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

            surface.setDrawColor(1f, 1f, 1f)
            surface.drawQuad(x0 - 5f, y0 - 5f, 10f, 10f)
            surface.drawQuad(x1 - 5f, y1 - 5f, 10f, 10f)
            surface.drawLine(x0, y0, x1, y1)
        }
    }

    fun onBodyUpdated(xCenter: Float, yCenter: Float, xCenterLast: Float, yCenterLast: Float, angle: Float)
    {
        if (this is SceneEntity)
        {
            x = xCenter
            y = yCenter
            rotation = angle
            set(SceneEntity.POSITION_UPDATED or SceneEntity.ROTATION_UPDATED)
        }
    }

    override fun onCollision(engine: PulseEngine, otherEntity: SceneEntity, result: CollisionResult) { }
}