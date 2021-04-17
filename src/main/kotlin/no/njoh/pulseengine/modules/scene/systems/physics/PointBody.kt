package no.njoh.pulseengine.modules.scene.systems.physics

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.SpatialGrid
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
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
            onBodyUpdated(point.x, point.y, point.xLast, point.yLast, 0f)
        }
    }

    override fun update(engine: PulseEngine, spatialGrid: SpatialGrid, gravity: Float, physicsIterations: Int, worldWidth: Int, worldHeight: Int)
    {
        if (bodyType == BodyType.STATIC)
            return

        val drag = 1f - drag
        val xNow = point.x
        val yNow = point.y
        val xVel = (xNow - point.xLast) * drag + point.xAcc
        val yVel = (yNow - point.yLast) * drag + point.yAcc
        val xPoint = xNow + xVel
        val yPoint = yNow + yVel

        point.xVel = xVel
        point.yVel = yVel
        point.xLast = xNow
        point.yLast = yNow
        point.xLastActual = xNow
        point.yLastActual = yNow
        point.x = xPoint
        point.y = yPoint
        point.xAcc = 0f
        point.yAcc = gravity

        // Handle world size constraint
        if (xPoint < worldWidth * -0.5f || xPoint > worldWidth * 0.5f || yPoint < worldHeight * -0.5f || yPoint > worldHeight * 0.5f)
            bodyType = BodyType.STATIC

        // Handle collision
        val extraWidth = abs(xVel)
        val extraHeight = abs(yVel)
        spatialGrid.query(xPoint, yPoint, extraWidth * 2, extraHeight * 2)
        {
            if (it is RigidBody &&
                xPoint < it.shape.xMax + extraWidth &&
                xPoint > it.shape.xMin - extraWidth &&
                yPoint < it.shape.yMax + extraHeight &&
                yPoint > it.shape.yMin - extraHeight
            ) {
                BodyInteraction.detectAndResolve(this, it)?.let { result ->
                    onCollision(engine, it, result)
                    if (this is SceneEntity)
                        it.onCollision(engine, this, result)
                }
            }
        }

        onBodyUpdated(point.x, point.y, point.xLast, point.yLast, 0f)
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

    // Default implementation
    override fun onCollision(engine: PulseEngine, otherEntity: SceneEntity, result: CollisionResult) { }
}

data class Point(
    // Current position
    var x: Float = 0f,
    var y: Float = 0f,

    // Last position, used to calculate velocity
    var xLast: Float = 0f,
    var yLast: Float = 0f,

    // Actual last position (updated once per frame, and does not change when direction and velocity changes)
    var xLastActual: Float = 0f,
    var yLastActual: Float = 0f,

    // Current velocity, calculated from last position and does not change while resolving collisions
    var xVel: Float = 0f,
    var yVel: Float = 0f,

    // Acceleration
    var xAcc: Float = 0f,
    var yAcc: Float = 0f
)