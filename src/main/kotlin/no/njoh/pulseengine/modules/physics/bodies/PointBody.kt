package no.njoh.pulseengine.modules.physics.bodies

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.modules.physics.*
import no.njoh.pulseengine.modules.physics.BodyType.STATIC
import no.njoh.pulseengine.modules.physics.shapes.PointShape
import kotlin.math.abs

interface PointBody : PhysicsBody
{
    override val shape: PointShape

    override fun init(engine: PulseEngine)
    {
        if (this is Spatial)
        {
            shape.init(x, y)
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

        engine.scene.forEachEntityNearby(x, y, xVel * 2, yVel * 2)
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

    override fun render(engine: PulseEngine, surface: Surface)
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
        if (this is SceneEntity && this is Spatial)
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

    override fun getMass() = shape.mass

    override fun onCollision(engine: PulseEngine, otherBody: PhysicsBody, result: ContactResult) { }
}