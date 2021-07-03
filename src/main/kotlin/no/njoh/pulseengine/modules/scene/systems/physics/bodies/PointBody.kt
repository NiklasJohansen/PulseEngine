package no.njoh.pulseengine.modules.scene.systems.physics.bodies

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.entities.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.modules.scene.systems.physics.*
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType.STATIC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PointShape
import org.joml.Vector2f
import kotlin.math.abs

interface PointBody : PhysicsBody
{
    val shape: PointShape

    override fun init(engine: PulseEngine)
    {
        if (this is SceneEntity)
        {
            shape.x = x
            shape.y = y
            shape.mass = density
        }
    }

    override fun beginStep(engine: PulseEngine, timeStep: Float, gravity: Float)
    {
        if (bodyType == STATIC)
            return

        val drag = 1f - drag
        val xNow = shape.x
        val yNow = shape.y
        val xVel = (xNow - shape.xLast) * drag + shape.xAcc * timeStep * timeStep
        val yVel = (yNow - shape.yLast) * drag + shape.yAcc * timeStep * timeStep

        shape.xVel = xVel
        shape.yVel = yVel
        shape.xLast = xNow
        shape.yLast = yNow
        shape.xLastActual = xNow
        shape.yLastActual = yNow
        shape.x = xNow + xVel
        shape.y = yNow + yVel
        shape.xAcc = 0f
        shape.yAcc = gravity
    }

    override fun iterateStep(engine: PulseEngine, iteration: Int, totalIterations: Int, worldWidth: Int, worldHeight: Int)
    {
        if (bodyType == STATIC || iteration > 0)
            return

        val x = shape.x
        val y = shape.y
        val xVel = abs(x - shape.xLast)
        val yVel = abs(y - shape.yLast)
        val xMin = x - xVel
        val yMin = y - yVel
        val xMax = x + xVel
        val yMax = y + yVel

        engine.scene.forEachNearbyEntity(x, y, xVel * 2, yVel * 2)
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

        onBodyUpdated()
    }

    override fun render(engine: PulseEngine, surface: Surface2D)
    {
        surface.setDrawColor(0.5f, 0.5f, 1f, 0.1f)
        surface.drawLine(shape.x, shape.y, shape.xLast, shape.yLast)

        val dotSize = 2f
        surface.setDrawColor(0.5f, 1f, 0.5f, 1f)
        surface.drawLine(shape.x, shape.y, shape.xLastActual, shape.yLastActual)
        surface.drawQuad(shape.x - 0.5f * dotSize, shape.y - 0.5f * dotSize, dotSize, dotSize)
    }

    fun onBodyUpdated()
    {
        if (this is SceneEntity)
        {
            x = shape.x
            y = shape.y
            set(POSITION_UPDATED)
        }
    }

    override fun wakeUp() { /* Point bodies do not sleep */ }

    override fun hasOverlappingAABB(xMin: Float, yMin: Float, xMax: Float, yMax: Float): Boolean
    {
        val xVel = abs(shape.xVel)
        val yVel = abs(shape.yVel)
        return xMin < shape.x + xVel && xMax > shape.x - xVel && yMin < shape.y + yVel && yMax > shape.y - yMin
    }

    override fun setPoint(index: Int, x: Float, y: Float)
    {
        shape.x = x
        shape.y = y
    }

    override fun getPoint(index: Int): Vector2f? =
        PointShape.reusableVector.set(shape.x, shape.y)

    override fun getPointCount() = 1

    override fun getMass() = shape.mass

    override fun onCollision(engine: PulseEngine, otherBody: PhysicsBody, result: ContactResult) { }
}