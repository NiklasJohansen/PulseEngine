package no.njoh.pulseengine.modules.scene.systems.physics.bodies

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.physics.*
import org.joml.Vector2f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class CircleShape(
    var x: Float = 0f,
    var y: Float = 0f,
    var xLast: Float = 0f,
    var yLast: Float = 0f,
    var xAcc: Float = 0f,
    var yAcc: Float = 0f,
    var rot: Float = 0f,
    var rotLast: Float = 0f,
    var lastRotVel: Float = 0f,
    var radius: Float = 50f
) {
    companion object { val reusableVector = Vector2f() }
}

interface CircleBody : Body
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
        // Handle world size constraint
        if (shape.x < worldWidth * -0.5f || shape.x > worldWidth * 0.5f || shape.y < worldHeight * -0.5f || shape.y > worldHeight * 0.5f)
            bodyType = BodyType.STATIC

        // Handle collision
        updateCollisions(engine)

        // Update last angular velocity
        shape.lastRotVel = shape.rot - shape.rotLast

        // Notify body updated on last iteration
        if (iteration == totalIterations - 1)
            onBodyUpdated(shape)
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
            if (it !== this && it is Body && it.hasOverlappingAABB(xMin, yMin, xMax, yMax))
            {
                BodyInteraction.detectAndResolve(this, it)?.let { result ->
                    onCollision(engine, it, result)
                    it.onCollision(engine, this, result)
                }
            }
        }
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
        surface.setDrawColor(1f, 1f, 1f, 1f)

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
            set(SceneEntity.POSITION_UPDATED)
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

    override fun onCollision(engine: PulseEngine, otherBody: Body, result: CollisionResult) { }
}