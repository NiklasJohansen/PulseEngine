package no.njoh.pulseengine.modules.scene.systems.physics.bodies

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.physics.*
import org.joml.Vector2f
import kotlin.math.abs

interface PointBody : Body
{
    val point: Point

    override fun init()
    {
        if (this is SceneEntity)
        {
            point.x = x
            point.y = y
            onBodyUpdated(point)
        }
    }

    override fun beginStep(timeStep: Float, gravity: Float)
    {
        val drag = 1f - drag
        val xNow = point.x
        val yNow = point.y
        val xVel = (xNow - point.xLast) * drag + point.xAcc * timeStep * timeStep
        val yVel = (yNow - point.yLast) * drag + point.yAcc * timeStep * timeStep

        point.xVel = xVel
        point.yVel = yVel
        point.xLast = xNow
        point.yLast = yNow
        point.xLastActual = xNow
        point.yLastActual = yNow
        point.x = xNow + xVel
        point.y = yNow + yVel
        point.xAcc = 0f
        point.yAcc = gravity
    }

    override fun iterateStep(iteration: Int, totalIterations: Int, engine: PulseEngine, worldWidth: Int, worldHeight: Int)
    {
        if (iteration > 0)
            return

        val x = point.x
        val y = point.y
        val xVel = abs(x - point.xLast)
        val yVel = abs(y - point.yLast)
        val xMin = x - xVel
        val yMin = y - yVel
        val xMax = x + xVel
        val yMax = y + yVel

        engine.scene.forEachNearbyEntity(x, y, xVel * 2, yVel * 2)
        {
            if (it !== this && it is Body && it.hasOverlappingAABB(xMin, yMin, xMax, yMax))
            {
                BodyInteraction.detectAndResolve(this, it)?.let { result ->
                    onCollision(engine, it, result)
                    it.onCollision(engine, this, result)
                }
            }
        }

        // Notify body updated on last iteration
        if (iteration == totalIterations - 1)
            onBodyUpdated(point)
    }

    override fun render(surface: Surface2D)
    {
        surface.setDrawColor(0.5f, 0.5f, 1f, 0.1f)
        surface.drawLine(point.x, point.y, point.xLast, point.yLast)

        val dotSize = 2f
        surface.setDrawColor(0.5f, 1f, 0.5f, 1f)
        surface.drawLine(point.x, point.y, point.xLastActual, point.yLastActual)
        surface.drawQuad(point.x - 0.5f * dotSize, point.y - 0.5f * dotSize, dotSize, dotSize)
    }

    fun onBodyUpdated(point: Point)
    {
        if (this is SceneEntity)
        {
            x = point.x
            y = point.y
            set(SceneEntity.POSITION_UPDATED)
        }
    }

    override fun hasOverlappingAABB(xMin: Float, yMin: Float, xMax: Float, yMax: Float): Boolean
    {
        val xVel = abs(point.xVel)
        val yVel = abs(point.yVel)
        return xMin < point.x + xVel && xMax > point.x - xVel && yMin < point.y + yVel && yMax > point.y - yMin
    }

    override fun setPoint(index: Int, x: Float, y: Float)
    {
        point.x = x
        point.y = y
    }

    override fun getPoint(index: Int): Vector2f? =
        Point.reusableVector.set(point.x, point.y)

    override fun getPointCount() = 1

    override fun onCollision(engine: PulseEngine, otherBody: Body, result: CollisionResult) { }
}