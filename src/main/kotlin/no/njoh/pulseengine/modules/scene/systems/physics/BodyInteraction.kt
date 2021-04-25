package no.njoh.pulseengine.modules.scene.systems.physics

import no.njoh.pulseengine.modules.scene.systems.physics.BodyType.STATIC
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.Body
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.PointBody
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.RigidBody
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.ConvexPolygonShape
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.N_POINT_FIELDS
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.X
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.X_ACC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.X_LAST
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.Y
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.Y_ACC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.Y_LAST
import no.njoh.pulseengine.util.MathUtil.getLineSegmentIntersection
import no.njoh.pulseengine.util.MathUtil.pointToLineDistance
import no.njoh.pulseengine.util.MathUtil.pointToLineSegmentDistanceSquared
import org.joml.Vector2f
import kotlin.math.abs
import kotlin.math.sqrt

object BodyInteraction
{
    private val result = CollisionResult()
    private val impulse = Vector2f(0f, 0f)

    fun detectAndResolve(b1: Body, b2: Body): CollisionResult?
    {
        when (b1)
        {
            is RigidBody -> when (b2)
            {
                is RigidBody -> return rigidBodyOnRigidBodyCollision(b1, b2)
                is PointBody -> return pointBodyOnRigidBodyCollision(b2, b1)
            }
            is PointBody -> when (b2)
            {
                is RigidBody -> return pointBodyOnRigidBodyCollision(b1, b2)
            }
        }

        return null
    }

    private fun rigidBodyOnRigidBodyCollision(b1: RigidBody, b2: RigidBody): CollisionResult?
    {
        when (b1.shape)
        {
            is ConvexPolygonShape -> when (b2.shape)
            {
                is ConvexPolygonShape -> return polygonOnPolygonCollision(b1, b2)
            }
        }

        return null
    }

    private fun pointBodyOnRigidBodyCollision(pointBody: PointBody, rigidBody: RigidBody): CollisionResult?
    {
        val rbPoints = rigidBody.shape.points
        val pbPoint = pointBody.point
        val xPoint = pbPoint.x
        val yPoint = pbPoint.y
        val xPointLastActual = pbPoint.xLastActual
        val yPointLastActual = pbPoint.yLastActual
        val indexOfLastBoundaryPoint = (rigidBody.shape.nBoundaryPoints - 1) * N_POINT_FIELDS

        // Both current and last position of point may be inside a moving body and line segment intersection is not enough
        val currentAndLastPointIsInside = rigidBody.shape.isInside(xPoint, yPoint) && rigidBody.shape.isInside(xPointLastActual, yPointLastActual)

        // Find edge closest to point
        var edgePointIndex1 = -1
        var minDist = Float.MAX_VALUE
        var xIntersection = Float.MAX_VALUE
        var yIntersection = -1f
        var xEdge0 = rbPoints[indexOfLastBoundaryPoint + X]
        var yEdge0 = rbPoints[indexOfLastBoundaryPoint + Y]

        rigidBody.shape.forEachBoundaryPoint { i ->
            val xEdge1 = this[i + X]
            val yEdge1 = this[i + Y]

            // Distance from point to edge
            val distance = pointToLineSegmentDistanceSquared(xPointLastActual, yPointLastActual, xEdge0, yEdge0, xEdge1, yEdge1)
            if (distance < minDist)
            {
                if (currentAndLastPointIsInside)
                {
                    minDist = distance
                    edgePointIndex1 = i
                }
                else
                {
                    getLineSegmentIntersection(xPointLastActual, yPointLastActual, xPoint, yPoint, xEdge0, yEdge0, xEdge1, yEdge1)?.let()
                    {
                        xIntersection = it.x
                        yIntersection = it.y
                        minDist = distance
                        edgePointIndex1 = i
                    }
                }
            }

            xEdge0 = xEdge1
            yEdge0 = yEdge1
        }

        // No edge found
        if (edgePointIndex1 == -1)
            return null

        val edgePointIndex0 = if (edgePointIndex1 == 0) indexOfLastBoundaryPoint else edgePointIndex1 - N_POINT_FIELDS

        val x0 = rbPoints[edgePointIndex0 + X]
        val y0 = rbPoints[edgePointIndex0 + Y]
        val x1 = rbPoints[edgePointIndex1 + X]
        val y1 = rbPoints[edgePointIndex1 + Y]

        // Calculate collision normal and response vector
        val depth = sqrt(pointToLineDistance(xPoint, yPoint, x0, y0, x1, y1)) + 0.0001f
        var xNormal = y0 - y1
        var yNormal = x1 - x0
        val invLength = 1f / sqrt(xNormal * xNormal + yNormal * yNormal)
        xNormal *= invLength
        yNormal *= invLength
        val xResponse = xNormal * depth
        val yResponse = yNormal * depth

        // Use the masses of the bodies to determine the ratio of position correction
        val invMass= 1.0f / (rigidBody.mass + pointBody.mass)
        val pointRatio = if (rigidBody.bodyType == STATIC) 1f else rigidBody.mass * invMass
        val bodyRatio = if (pointBody.bodyType == STATIC) 1f else pointBody.mass * invMass

        if (rigidBody.bodyType != STATIC && bodyRatio != 0f)
        {
            // Calculate where on the edge the collision point lies (0.0 - 1.0). If-check prevents divide by zero.
            val t =
                if (abs(x0 - x1) > abs(y0 - y1)) (xPoint - xResponse - x0) / (x1 - x0)
                else
                    (yPoint - yResponse - y0) / (y1 - y0)

            // Calculate a scaling factor that ensures that the point lies on the edge after the collision response
            val lambda = 1.0f / (t * t + (1 - t) * (1 - t)) * bodyRatio

            // Correct edge point positions
            rbPoints[edgePointIndex0 + X] -= xResponse * lambda * (1 - t)
            rbPoints[edgePointIndex0 + Y] -= yResponse * lambda * (1 - t)
            rbPoints[edgePointIndex1 + X] -= xResponse * lambda * t
            rbPoints[edgePointIndex1 + Y] -= yResponse * lambda * t

            // Wake up body
            rigidBody.shape.isSleeping = false
        }

        if (pointBody.bodyType != STATIC && pointRatio != 0f)
        {
            val xPointDir = xPoint - xPointLastActual
            val yPointDir = yPoint - yPointLastActual
            val dot = xPointDir * xNormal + yPointDir * yNormal

            // If dot product between edge normal and travel direction is more than 0, then the point is moving away from the edge
            if (dot > 0f)
            {
                if (xIntersection != Float.MAX_VALUE)
                {
                    // Only correct lastActual to intersection point
                    pbPoint.xLastActual = xIntersection + xNormal * 0.001f
                    pbPoint.yLastActual = yIntersection + yNormal * 0.001f
                }
            }
            else if (xIntersection == Float.MAX_VALUE)
            {
                // If no line intersection was found, then both last and current point positions are inside the body
                pbPoint.x += xResponse * pointRatio
                pbPoint.y += yResponse * pointRatio
            }
            else
            {
                val friction = 1f - (rigidBody.friction * pointBody.friction)
                val xV = pbPoint.xVel * friction
                val yV = pbPoint.yVel * friction
                val vnDot = xV * xNormal + yV * yNormal
                val xN = xNormal * vnDot
                val yN = yNormal * vnDot
                val xP = (xV - xN)
                val yP = (yV - yN)

                // Calculate new velocity vector
                val xVelNew = xP - xN * pointBody.elasticity
                val yVelNew = yP - yN * pointBody.elasticity

                // Correct point position
                pbPoint.x = xIntersection + xNormal * 1f
                pbPoint.y = yIntersection + yNormal * 1f

                // Update velocity
                pbPoint.xLast = pbPoint.x - xVelNew
                pbPoint.yLast = pbPoint.y - yVelNew
            }
        }

        return result.set (
            x = pbPoint.x,
            y = pbPoint.y,
            xNormal = xNormal,
            yNormal = yNormal,
            depth = depth
        )
    }

    private fun polygonOnPolygonCollision(aBody: RigidBody, bBody: RigidBody): CollisionResult?
    {
        var depth = Float.MAX_VALUE
        var xNormal = 0f
        var yNormal = 0f

        var edgeBody: RigidBody? = null
        var edgePoint0 = -1
        var edgePoint1 = -1

        var xAxis = 0f
        var yAxis = 0f
        val aPoints = aBody.shape.points
        val bPoints = bBody.shape.points
        val aEdgeCount = aBody.shape.nBoundaryPoints
        val bEdgeCount = bBody.shape.nBoundaryPoints
        val nEdges = aEdgeCount + bEdgeCount

        var edgeIndex = 0
        while (edgeIndex < nEdges)
        {
            val points = if (edgeIndex < aEdgeCount) aPoints else bPoints
            val pointIndex = if (edgeIndex < aEdgeCount) edgeIndex else edgeIndex - aEdgeCount
            val edgeCount = if (edgeIndex < aEdgeCount) aEdgeCount else bEdgeCount
            val p0 = pointIndex * N_POINT_FIELDS
            val p1 = ((pointIndex + 1) % edgeCount) * N_POINT_FIELDS

            // Calculate normal vector of the edge - this is the axis to project each point on
            xAxis = points[p0 + Y] - points[p1 + Y]
            yAxis = points[p1 + X] - points[p0 + X]
            val invLength = 1.0f / sqrt(xAxis * xAxis + yAxis * yAxis)
            xAxis *= invLength
            yAxis *= invLength

            // Project points of body A onto the axis
            var aDot = xAxis * aPoints[X] + yAxis * aPoints[Y]
            var aMin = aDot
            var aMax = aDot
            aBody.shape.forEachPoint(startIndex = 1) { i ->
                aDot = xAxis * this[i + X] + yAxis * this[i + Y]
                if (aDot < aMin) aMin = aDot
                if (aDot > aMax) aMax = aDot
            }

            // Project points of body B onto the axis
            var bDot = xAxis * bPoints[X] + yAxis * bPoints[Y]
            var bMin = bDot
            var bMax = bDot
            bBody.shape.forEachPoint(startIndex = 1) { i ->
                bDot = xAxis * this[i + X] + yAxis * this[i + Y]
                if (bDot < bMin) bMin = bDot
                if (bDot > bMax) bMax = bDot
            }

            // Calculate interval distance
            val distance = if (aMin < bMin) bMin - aMax else aMin - bMax

            // Found a separating axis - return as there is no collision
            if (distance > 0f)
                return null

            // Store the collision information
            val absDistance = abs(distance)
            if (absDistance < depth)
            {
                depth = absDistance
                xNormal = xAxis
                yNormal = yAxis
                edgeBody = if (edgeIndex < aEdgeCount) aBody else bBody
                edgePoint0 = p0
                edgePoint1 = p1
            }

            edgeIndex++
        }

        if (edgeBody == null)
            return null

        // Inverts the collision normal if it doesn't point in the direction of the pointBody
        val pointBody = if (edgeBody === aBody) bBody else aBody
        val xDelta = pointBody.shape.xCenter - edgeBody.shape.xCenter
        val yDelta = pointBody.shape.yCenter - edgeBody.shape.yCenter
        val dot = xNormal * xDelta + yNormal * yDelta
        if (dot < 0)
        {
            xNormal = 0f - xNormal
            yNormal = 0f - yNormal
        }

        // Find the point closest to the edge
        var pointIndex = -1
        var minDist = Float.MAX_VALUE
        val xCenter = edgeBody.shape.xCenter
        val yCenter = edgeBody.shape.yCenter
        pointBody.shape.forEachBoundaryPoint { i ->
            val xd = this[i + X] - xCenter
            val yd = this[i + Y] - yCenter
            val dist = xNormal * xd + yNormal * yd
            if (dist < minDist)
            {
                minDist = dist
                pointIndex = i
            }
        }

        if (pointIndex == -1)
            return null

        //////////////////////////////// Resolve collision ////////////////////////////////

        // Calculate response vector
        val xResponse = xNormal * depth
        val yResponse = yNormal * depth

        // Use the masses of the bodies to determine the ratio of position correction for each body
        val invTotalMass= 1.0f / (edgeBody.mass + pointBody.mass)
        val pointRatio = if (edgeBody.bodyType == STATIC) 1f else edgeBody.mass * invTotalMass
        val edgeRatio = if (pointBody.bodyType == STATIC) 1f else pointBody.mass * invTotalMass

        val pPoints = pointBody.shape.points
        val xPoint = pPoints[pointIndex + X]
        val yPoint = pPoints[pointIndex + Y]

        if (edgeBody.bodyType != STATIC && edgeRatio != 0f)
        {
            val ePoints = edgeBody.shape.points
            val x0 = ePoints[edgePoint0 + X]
            val y0 = ePoints[edgePoint0 + Y]
            val x1 = ePoints[edgePoint1 + X]
            val y1 = ePoints[edgePoint1 + Y]

            // Calculate where on the edge the collision point lies (0.0 - 1.0). If-check prevents divide by zero.
            val t =
                if (abs(x0 - x1) > abs(y0 - y1))
                    (xPoint - xResponse - x0) / (x1 - x0)
                else
                    (yPoint - yResponse - y0) / (y1 - y0)

            // Calculate a scaling factor that ensures that the point lies on the edge after the collision response
            val lambda = 1.0f / (t * t + (1 - t) * (1 - t)) * edgeRatio

            // Correct point position
            ePoints[edgePoint0 + X] -= xResponse * lambda * (1 - t)
            ePoints[edgePoint0 + Y] -= yResponse * lambda * (1 - t)
            ePoints[edgePoint1 + X] -= xResponse * lambda * t
            ePoints[edgePoint1 + Y] -= yResponse * lambda * t

            // Wake up body
            edgeBody.shape.isSleeping = false
        }

        if (pointBody.bodyType != STATIC && pointRatio != 0f)
        {
            // Correct point position
            pPoints[pointIndex + X] += xResponse * pointRatio
            pPoints[pointIndex + Y] += yResponse * pointRatio

            // Calculate and apply friction impulse
            val xVel = xPoint - pPoints[pointIndex + X_LAST]
            val yVel = yPoint - pPoints[pointIndex + Y_LAST]
            val frictionCoefficient = edgeBody.friction * pointBody.friction
            val frictionImpulse = calculateFrictionImpulse(xVel, yVel, yNormal, -xNormal, depth, frictionCoefficient)
            pPoints[pointIndex + X_ACC] += frictionImpulse.x
            pPoints[pointIndex + Y_ACC] += frictionImpulse.y

            // Wake up body
            pointBody.shape.isSleeping = false
        }

        return result.set(
            x = xPoint + xResponse,
            y = yPoint + yResponse,
            xNormal = xNormal,
            yNormal = yNormal,
            depth = depth
        )
    }

    private fun calculateFrictionImpulse(
        xVelocity: Float,
        yVelocity: Float,
        xEdgeDir: Float,
        yEdgeDir: Float,
        penetrationDepth: Float,
        frictionCoefficient: Float
    ): Vector2f {
        // Only add friction impulse if point is in motion
        if (xVelocity != 0f || yVelocity != 0f)
        {
            // Direction of motion normalized
            val len = 1.0f / sqrt(xVelocity * xVelocity + yVelocity * yVelocity)
            val xVelDir = xVelocity * len
            val yVelDir = yVelocity * len

            // Dot product between velocity and edge direction multiplied by penetration depth and friction coefficient
            val friction = (xVelDir * xEdgeDir + yVelDir * yEdgeDir) * penetrationDepth * frictionCoefficient

            // Friction impulse along the negative edge direction
            impulse.set(-xEdgeDir * friction, -yEdgeDir * friction)
        }
        else impulse.set(0f, 0f)

        return impulse
    }
}

data class CollisionResult(
    var x: Float = 0f,
    var y: Float = 0f,
    var xNormal: Float = 0f,
    var yNormal: Float = 0f,
    var depth: Float = 0f
) {
    fun set(x: Float, y: Float, xNormal: Float, yNormal: Float, depth: Float): CollisionResult
    {
        this.x = x
        this.y = y
        this.xNormal = xNormal
        this.yNormal = yNormal
        this.depth = depth
        return this
    }
}
