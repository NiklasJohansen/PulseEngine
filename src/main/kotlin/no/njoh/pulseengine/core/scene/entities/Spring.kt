package no.njoh.pulseengine.core.scene.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.systems.physics.BodyType
import no.njoh.pulseengine.core.scene.systems.physics.PhysicsEntity
import no.njoh.pulseengine.core.scene.systems.physics.bodies.PhysicsBody
import kotlin.math.sqrt

open class Spring : SceneEntity(), PhysicsEntity
{
    var bodyRef0 = 0L
    var bodyRef1 = 0L
    var bodyPoint0 = 0
    var bodyPoint1 = 0
    var stiffness = 1f

    @JsonIgnore
    private var targetLength = Float.NaN

    override fun init(engine: PulseEngine) { }

    override fun beginStep(engine: PulseEngine, timeStep: Float, gravity: Float)
    {
        if (targetLength.isNaN())
            getPoints(engine) { x0, y0, x1, y1 -> targetLength = sqrt((x0 - x1) * (x0 - x1) + (y0 - y1) * (y0 - y1)) }
    }

    override fun iterateStep(engine: PulseEngine, iteration: Int, totalIterations: Int, worldWidth: Int, worldHeight: Int)
    {
        val body0 = engine.scene.getEntityOfType<PhysicsBody>(bodyRef0)
        val body1 = engine.scene.getEntityOfType<PhysicsBody>(bodyRef1)

        body0?.shape?.getPoint(bodyPoint0)?.let { p0 ->
            val p0x = p0.x
            val p0y = p0.y
            body1?.shape?.getPoint(bodyPoint1)?.let { p1 ->
                // Calculate displacement
                val xDelta = p0x - p1.x
                val yDelta = p0y - p1.y
                val actualLength = sqrt(xDelta * xDelta + yDelta * yDelta).takeIf { it != 0f } ?: 0.000001f
                val deltaLength = (targetLength - actualLength) * 0.5f * stiffness
                val xDisp = (xDelta / actualLength) * deltaLength
                val yDisp = (yDelta / actualLength) * deltaLength

                // Calculate displacement ratios based on mass
                val mass0 = body0.getMass()
                val mass1 = body1.getMass()
                val invTotalMass= 1.0f / (mass0 + mass1)
                val ratio0 = if (body1.bodyType == BodyType.STATIC) 1f else mass1 * invTotalMass
                val ratio1 = if (body0.bodyType == BodyType.STATIC) 1f else mass0 * invTotalMass

                if (body0.bodyType != BodyType.STATIC)
                {
                    body0.shape.setPoint(bodyPoint0, p0x + xDisp * ratio0, p0y + yDisp * ratio0)
                    body0.wakeUp()
                }

                if (body1.bodyType != BodyType.STATIC)
                {
                    body1.shape.setPoint(bodyPoint1, p1.x - xDisp * ratio1, p1.y - yDisp * ratio1)
                    body1.wakeUp()
                }
            }
        }
    }

    override fun render(engine: PulseEngine, surface: Surface2D)
    {
        getPoints(engine) { x0, y0, x1, y1 ->
            if (x0 == 0f || y0 == 0f || x1 == 0f || y1 == 0f )
                return

            if (stiffness == 1f)
            {
                // Draw simple line
                surface.setDrawColor(1f, 1f, 1f, 1f)
                surface.drawLine(x0, y0, x1, y1)
                return
            }

            val xDelta = x1 - x0
            val yDelta = y1 - y0
            val length = sqrt(xDelta * xDelta + yDelta * yDelta)
            val xNormal = (yDelta / length) * 50f
            val yNormal = (-xDelta / length) * 50f
            val nPoints = 40
            val xStep = xDelta / nPoints
            val yStep = yDelta / nPoints
            var xLast = x0
            var yLast = y0

            // Draw spring as zigzag line
            for (i in 1 until nPoints + 1)
            {
                val x = x0 + xStep * i - xNormal * ((i % 3) - 1f)
                val y = y0 + yStep * i - yNormal * ((i % 3) - 1f)
                surface.setDrawColor(1f, 1f, 1f, 1f)
                surface.drawLine(xLast, yLast, x, y)
                xLast = x
                yLast = y
            }
        }
    }

    private inline fun getPoints(engine: PulseEngine, block: (x0: Float, y0: Float, x1: Float, y1: Float) -> Unit)
    {
        val body0 = engine.scene.getEntityOfType<PhysicsBody>(bodyRef0)
        val body1 = engine.scene.getEntityOfType<PhysicsBody>(bodyRef1)
        body0?.shape?.getPoint(bodyPoint0)?.let { p0 ->
            val x0 = p0.x
            val y0 = p0.y
            body1?.shape?.getPoint(bodyPoint1)?.let { p1 ->
                block(x0, y0, p1.x, p1.y)
            }
        }
    }
}