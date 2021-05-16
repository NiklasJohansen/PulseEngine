package no.njoh.pulseengine.modules.scene.systems.physics

import no.njoh.pulseengine.modules.scene.systems.physics.BodyType.DYNAMIC
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType.STATIC
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.*
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.N_POINT_FIELDS
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.X
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.X_LAST
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.Y
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.PolygonShape.Companion.Y_LAST
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.MathUtil
import no.njoh.pulseengine.util.MathUtil.getLineSegmentCircleIntersection
import no.njoh.pulseengine.util.MathUtil.getLineSegmentIntersection
import no.njoh.pulseengine.util.MathUtil.pointToLineDistanceSquared
import no.njoh.pulseengine.util.MathUtil.pointToLineSegmentDistanceSquared
import org.joml.Vector2f
import kotlin.math.*

object ContactSolver
{
    private val result = ContactResult()
    private val impulse = Vector2f(0f, 0f)

    fun solve(b0: PhysicsBody, b1: PhysicsBody): ContactResult?
    {
        when (b0)
        {
            is PolygonBody -> when (b1)
            {
                is PolygonBody -> return solvePolygonOnPolygon(b0, b1)
                is PointBody -> return solvePointOnPolygon(b1, b0)
                is CircleBody -> return solveCircleOnPolygon(b1, b0)
            }
            is PointBody -> when (b1)
            {
                is PointBody -> return null
                is PolygonBody -> return solvePointOnPolygon(b0, b1)
                is CircleBody -> return solveCircleOnPoint(b1, b0)
            }
            is CircleBody -> when (b1)
            {
                is PolygonBody -> return solveCircleOnPolygon(b0, b1)
                is PointBody -> return solveCircleOnPoint(b0, b1)
                is CircleBody -> return solveCircleOnCircle(b0, b1)
            }
        }

        Logger.error("Missing body interaction between ${b0::class.simpleName} and ${b1::class.simpleName}")
        return null
    }

    private fun solvePointOnPolygon(pointBody: PointBody, polygonBody: PolygonBody): ContactResult?
    {
        val rbPoints = polygonBody.shape.points
        val pbPoint = pointBody.shape
        val xPoint = pbPoint.x
        val yPoint = pbPoint.y
        val xPointLastActual = pbPoint.xLastActual
        val yPointLastActual = pbPoint.yLastActual
        val indexOfLastBoundaryPoint = (polygonBody.shape.nBoundaryPoints - 1) * N_POINT_FIELDS

        // Both current and last position of point may be inside a moving body and line segment intersection is not enough
        val currentAndLastPointIsInside = polygonBody.shape.isInside(xPoint, yPoint) && polygonBody.shape.isInside(xPointLastActual, yPointLastActual)

        // Find edge closest to point
        var edgePointIndex1 = -1
        var minDist = Float.MAX_VALUE
        var xIntersection = Float.MAX_VALUE
        var yIntersection = -1f
        var xEdge0 = rbPoints[indexOfLastBoundaryPoint + X]
        var yEdge0 = rbPoints[indexOfLastBoundaryPoint + Y]

        polygonBody.shape.forEachBoundaryPoint { i ->
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
        val depth = sqrt(pointToLineDistanceSquared(xPoint, yPoint, x0, y0, x1, y1)) + 0.0001f
        var xNormal = y0 - y1
        var yNormal = x1 - x0
        val invLength = 1f / sqrt(xNormal * xNormal + yNormal * yNormal)
        xNormal *= invLength
        yNormal *= invLength
        val xResponse = xNormal * depth
        val yResponse = yNormal * depth

        // Use the masses of the bodies to determine the ratio of position correction
        val invMass = 1.0f / (polygonBody.shape.mass + pointBody.shape.mass)
        val pointRatio = if (polygonBody.bodyType == STATIC) 1f else polygonBody.shape.mass * invMass
        val bodyRatio = if (pointBody.bodyType == STATIC) 1f else pointBody.shape.mass * invMass

        if (polygonBody.bodyType != STATIC && bodyRatio != 0f)
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
                val coefficientOfFriction = 1f - (polygonBody.friction + pointBody.friction) * 0.5f

                val xV = pbPoint.xVel * coefficientOfFriction
                val yV = pbPoint.yVel * coefficientOfFriction
                val vnDot = xV * xNormal + yV * yNormal
                val xN = xNormal * vnDot
                val yN = yNormal * vnDot
                val xP = (xV - xN)
                val yP = (yV - yN)

                // Calculate new velocity vector
                val coefficientOfRestitution = (polygonBody.restitution + pointBody.restitution) * 0.5f
                val xVelNew = xP - xN * coefficientOfRestitution
                val yVelNew = yP - yN * coefficientOfRestitution

                // Correct point position
                pbPoint.x = xIntersection + xNormal
                pbPoint.y = yIntersection + yNormal

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

    private fun solvePolygonOnPolygon(aBody: PolygonBody, bBody: PolygonBody): ContactResult?
    {
        var depth = Float.MAX_VALUE
        var xNormal = 0f
        var yNormal = 0f

        var edgeBody: PolygonBody? = null
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
        val invTotalMass = 1.0f / (edgeBody.shape.mass + pointBody.shape.mass)
        val pointRatio = if (edgeBody.bodyType == STATIC) 1f else edgeBody.shape.mass * invTotalMass
        val edgeRatio = if (pointBody.bodyType == STATIC) 1f else pointBody.shape.mass * invTotalMass

        val coefficientOfFriction = (edgeBody.friction + pointBody.friction) * 0.5f

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

            // Add friction to point 0
            val xVel0 = x0 - ePoints[edgePoint0 + X_LAST]
            val yVel0 = y0 - ePoints[edgePoint0 + Y_LAST]
            val frictionImpulse0 = calculateFrictionImpulse(xVel0, yVel0, yNormal, -xNormal, depth, coefficientOfFriction)
            ePoints[edgePoint0 + X_LAST] -= frictionImpulse0.x * (1 - t)
            ePoints[edgePoint0 + Y_LAST] -= frictionImpulse0.y * (1 - t)

            // Add friction to point 1
            val xVel1 = x1 - ePoints[edgePoint1 + X_LAST]
            val yVel1 = y1 - ePoints[edgePoint1 + Y_LAST]
            val frictionImpulse1 = calculateFrictionImpulse(xVel1, yVel1, yNormal, -xNormal, depth, coefficientOfFriction)
            ePoints[edgePoint1 + X_LAST] -= frictionImpulse1.x * t
            ePoints[edgePoint1 + Y_LAST] -= frictionImpulse1.y * t
        }

        if (pointBody.bodyType != STATIC && pointRatio != 0f)
        {
            // Correct point position
            pPoints[pointIndex + X] += xResponse * pointRatio
            pPoints[pointIndex + Y] += yResponse * pointRatio

            // Calculate and apply friction impulse
            val xVel = xPoint - pPoints[pointIndex + X_LAST]
            val yVel = yPoint - pPoints[pointIndex + Y_LAST]
            val frictionImpulse = calculateFrictionImpulse(xVel, yVel, yNormal, -xNormal, depth, coefficientOfFriction)
            pPoints[pointIndex + X_LAST] -= frictionImpulse.x
            pPoints[pointIndex + Y_LAST] -= frictionImpulse.y
        }

        if (pointBody.bodyType == STATIC && edgeBody.bodyType == DYNAMIC)
        {
            // Flip normal if only edge body moved
            xNormal = 0f - xNormal
            yNormal = 0f - yNormal
        }

        return result.set(
            x = xPoint + xResponse,
            y = yPoint + yResponse,
            xNormal = xNormal,
            yNormal = yNormal,
            depth = depth
        )
    }

    private fun solveCircleOnCircle(aBody: CircleBody, bBody: CircleBody): ContactResult?
    {
        val aCircle = aBody.shape
        val bCircle = bBody.shape
        val aRadius = aCircle.radius
        val bRadius = bCircle.radius

        val xDelta = aCircle.x - bCircle.x
        val yDelta = aCircle.y - bCircle.y
        val distSquared = xDelta * xDelta + yDelta * yDelta
        val minDist = aRadius + bRadius

        if (distSquared <= minDist * minDist)
        {
            val dist = sqrt(distSquared)
            val depth = minDist - dist
            val xNormal = xDelta / dist
            val yNormal = yDelta / dist
            val invTotalMass = 1.0f / (aCircle.mass + bCircle.mass)
            val aRatio = if (bBody.bodyType == STATIC) 1f else bCircle.mass * invTotalMass
            val bRatio = if (aBody.bodyType == STATIC) 1f else aCircle.mass * invTotalMass
            val coefficientOfFriction = (aBody.friction + bBody.friction) * 0.5f
            val coefficientOfRestitution = (aBody.restitution + bBody.restitution) * 0.5f

            val aVelX = aCircle.x - aCircle.xLast
            val aVelY = aCircle.y - aCircle.yLast
            val bVelX = bCircle.x - bCircle.xLast
            val bVelY = bCircle.y - bCircle.yLast

            val xDeltaVel = aVelX - bVelX
            val yDeltaVel = aVelY - bVelY
            val velocityAlongNormal = xDeltaVel * xNormal + yDeltaVel * yNormal
            val impulse = velocityAlongNormal * (1.0f + coefficientOfRestitution)

            if (aBody.bodyType != STATIC)
                aCircle.update(xNormal, yNormal, depth, impulse, coefficientOfFriction, aRatio)

            if (bBody.bodyType != STATIC)
                bCircle.update(-xNormal, -yNormal, depth, impulse, coefficientOfFriction, bRatio)

            return result.set(
                x = aCircle.x + xNormal * aRadius,
                y = aCircle.y + yNormal * aRadius,
                xNormal = xNormal,
                yNormal = yNormal,
                depth = depth
            )
        }

        return null
    }

    private fun CircleShape.update(xNormal: Float, yNormal: Float, depth: Float, impulse: Float, coefficientOfFriction: Float, ratio: Float)
    {
        var xVel = x - xLast
        var yVel = y - yLast

        // Calculate velocity along tangential axis
        val vnDot = xVel * xNormal + yVel * yNormal
        val xTangentVel = xVel - (xNormal * vnDot)
        val yTangentVel = yVel - (yNormal * vnDot)

        // Signed length of tangential velocity
        val xTangent = -yNormal
        val yTangent = xNormal
        val velSign = sign(xTangentVel * xTangent + yTangentVel * yTangent)
        val tangentVel = sqrt(xTangentVel * xTangentVel + yTangentVel * yTangentVel) * velSign

        // Rotational velocity transformed from angle to world space
        val circumference = 2 * PI.toFloat() * radius
        var rotVel = lastRotVel / (2f * PI.toFloat()) * circumference

        // Difference between rotational and tangential velocity
        val velDiff = (rotVel - tangentVel) * coefficientOfFriction

        // Increase velocity along tangential axis and decrease rotational velocity
        xVel += xTangent * velDiff
        yVel += yTangent * velDiff
        rotVel -= velDiff

        // Update velocity vector with bounce impulse
        xVel -= xNormal * impulse * ratio
        yVel -= yNormal * impulse * ratio

        // Update rotational velocity
        rotLast = rot - ((rotVel / circumference) * 2f * PI.toFloat())

        // Correct circle position
        x += xNormal * depth * ratio
        y += yNormal * depth * ratio

        // Update velocity only if
        if (impulse < 0)
        {
            xLast = x - xVel
            yLast = y - yVel
        }
    }

    private fun solveCircleOnPolygon(circleBody: CircleBody, polygonBody: PolygonBody): ContactResult?
    {
        val points = polygonBody.shape.points
        val circle = circleBody.shape
        val xCircle = circle.x
        val yCircle = circle.y
        val radius = circle.radius

        val indexOfLastBoundaryPoint = (polygonBody.shape.nBoundaryPoints - 1) * N_POINT_FIELDS
        var xLast = points[indexOfLastBoundaryPoint + X]
        var yLast = points[indexOfLastBoundaryPoint + Y]
        var edgePointIndex1 = -1
        var minDist = Float.MAX_VALUE
        var xClosest = -1f
        var yClosest = -1f

        // Find closest point within radius of circle on rigid body
        polygonBody.shape.forEachBoundaryPoint { i ->
            val x = points[i + X]
            val y = points[i + Y]
            val point = MathUtil.closestPointOnLineSegment(xCircle, yCircle, x, y, xLast, yLast)
            val xDelta = xCircle - point.x
            val yDelta = yCircle - point.y
            val distSquared = xDelta * xDelta + yDelta * yDelta
            if (distSquared < radius * radius)
            {
                if (distSquared < minDist)
                {
                    minDist = distSquared
                    edgePointIndex1 = i
                    xClosest = point.x
                    yClosest = point.y
                }
            }
            xLast = x
            yLast = y
        }

        if (edgePointIndex1 == -1)
            return null // No collision

        // Calculate collision normal and penetration depth
        val xDelta = xClosest - xCircle
        val yDelta = yClosest - yCircle
        val dist = sqrt(xDelta * xDelta + yDelta * yDelta)
        val xNormal = xDelta / dist
        val yNormal = yDelta / dist
        val depth = dist - radius

        // Calculate mass ratios, friction restitution coefficients
        val invTotalMass = 1.0f / (polygonBody.shape.mass + circle.mass)
        val circleBodyRatio = if (polygonBody.bodyType == STATIC) 1f else polygonBody.shape.mass * invTotalMass
        val rigidBodyRatio = if (circleBody.bodyType == STATIC) 1f else circle.mass * invTotalMass
        val coefficientOfFriction = (polygonBody.friction + circleBody.friction) * 0.5f
        val coefficientOfRestitution = (polygonBody.restitution + circleBody.restitution) * 0.5f

        val edgePointIndex0 = if (edgePointIndex1 == 0) indexOfLastBoundaryPoint else edgePointIndex1 - N_POINT_FIELDS
        val ePoints = polygonBody.shape.points
        val x0 = ePoints[edgePointIndex0 + X]
        val y0 = ePoints[edgePointIndex0 + Y]
        val x1 = ePoints[edgePointIndex1 + X]
        val y1 = ePoints[edgePointIndex1 + Y]

        // Calculate where on the edge the collision point lies (0.0 - 1.0). If-check prevents divide by zero.
        val t = if (abs(x0 - x1) > abs(y0 - y1)) (xClosest - x0) / (x1 - x0) else (yClosest - y0) / (y1 - y0)

        if (polygonBody.bodyType != STATIC)
        {
            // Calculate a scaling factor that ensures that the point lies on the edge after the collision response
            val lambda = 1.0f / (t * t + (1 - t) * (1 - t)) * rigidBodyRatio

            // Correct edge point positions
            ePoints[edgePointIndex0 + X] -= xNormal * depth * lambda * (1 - t)
            ePoints[edgePointIndex0 + Y] -= yNormal * depth * lambda * (1 - t)
            ePoints[edgePointIndex1 + X] -= xNormal * depth * lambda * t
            ePoints[edgePointIndex1 + Y] -= yNormal * depth * lambda * t

            // Add friction to point 0
            val xVel0 = x0 - ePoints[edgePointIndex0 + X_LAST]
            val yVel0 = y0 - ePoints[edgePointIndex0 + Y_LAST]
            val frictionImpulse0 = calculateFrictionImpulse(xVel0, yVel0, yNormal, -xNormal, depth, coefficientOfFriction)
            ePoints[edgePointIndex0 + X_LAST] += frictionImpulse0.x * (1 - t)
            ePoints[edgePointIndex0 + Y_LAST] += frictionImpulse0.y * (1 - t)

            // Add friction to point 1
            val xVel1 = x1 - ePoints[edgePointIndex1 + X_LAST]
            val yVel1 = y1 - ePoints[edgePointIndex1 + Y_LAST]
            val frictionImpulse1 = calculateFrictionImpulse(xVel1, yVel1, yNormal, -xNormal, depth, coefficientOfFriction)
            ePoints[edgePointIndex1 + X_LAST] += frictionImpulse1.x * t
            ePoints[edgePointIndex1 + Y_LAST] += frictionImpulse1.y * t
        }

        if (circleBody.bodyType != STATIC)
        {
            // Calculate circle velocity before position is corrected
            val xVel = circle.x - circle.xLast
            val yVel = circle.y - circle.yLast

            // Correct circle position
            circle.x += xNormal * depth * circleBodyRatio
            circle.y += yNormal * depth * circleBodyRatio

            // Split velocity vector up into its normal and perpendicular components
            val vnDot = xVel * xNormal + yVel * yNormal
            val xN = xNormal * vnDot
            val yN = yNormal * vnDot
            var xP = (xVel - xN)
            var yP = (yVel - yN)

            // Rotational velocity transformed from angle to world space
            val circumference = 2 * PI.toFloat() * radius
            var rotVel = circle.lastRotVel / (2f * PI.toFloat()) * circumference

            // Signed length of perpendicular velocity
            val xEdgeDir = yNormal
            val yEdgeDir = -xNormal
            val velEdgeDot = sign(xP * xEdgeDir + yP * yEdgeDir)
            val perpendicularVel = sqrt(xP * xP + yP * yP) * velEdgeDot

            // Difference between rotational and perpendicular velocity
            val velDiff = (rotVel - perpendicularVel) * coefficientOfFriction

            // Decrease rotational velocity
            rotVel -= velDiff

            // Increase perpendicular velocity of circle
            val xVelChange = xEdgeDir * velDiff
            val yVelChange = yEdgeDir * velDiff
            xP += xVelChange
            yP += yVelChange

            // Nudge velocity of rigid body in opposite direction
            if (polygonBody.bodyType != STATIC)
            {
                ePoints[edgePointIndex0 + X_LAST] += xVelChange * (1f - t)
                ePoints[edgePointIndex0 + Y_LAST] += yVelChange * (1f - t)
                ePoints[edgePointIndex1 + X_LAST] += xVelChange * t
                ePoints[edgePointIndex1 + Y_LAST] += yVelChange * t
            }

            // Recombine perpendicular and normal velocities reflected about the edge normal
            circle.xLast = circle.x - (xP - xN * coefficientOfRestitution)
            circle.yLast = circle.y - (yP - yN * coefficientOfRestitution)

            // Update rotational velocity
            circle.rotLast = circle.rot - ((rotVel / circumference) * 2f * PI.toFloat())
        }

        return result.set(
            x = xClosest,
            y = yClosest,
            xNormal = xNormal,
            yNormal = yNormal,
            depth = depth
        )
    }

    private fun solveCircleOnPoint(circleBody: CircleBody, pointBody: PointBody): ContactResult?
    {
        val point = pointBody.shape
        val circle = circleBody.shape
        val radius = circle.radius
        val xDelta = point.x - circle.x
        val yDelta = point.y - circle.y
        val distanceSquared = xDelta * xDelta + yDelta * yDelta

        val intersection = getLineSegmentCircleIntersection(circle.x, circle.y, radius, point.xLastActual, point.yLastActual, point.x, point.y)
        if (distanceSquared > radius * radius && intersection == null)
            return null // No collision

        // Calculate collision normal, tangent and penetration depth
        val distance = sqrt(distanceSquared)
        var xNormal = xDelta / distance
        var yNormal = yDelta / distance
        val xTangent = yNormal
        val yTangent = -xNormal
        val depth = distance - radius + 0.1f

        if (intersection != null)
        {
            // Point is inside circe, calculate normal from point to center
            val xd = intersection.x - circle.x
            val yd = intersection.y - circle.y
            val dist = sqrt(xd * xd + yd * yd)
            xNormal = xd / dist
            yNormal = yd / dist
        }

        // Calculate mass ratios
        val invTotalMass = 1.0f / (point.mass + circle.mass)
        val circleRatio = if (pointBody.bodyType == STATIC) 1f else point.mass * invTotalMass
        val pointRatio = if (circleBody.bodyType == STATIC) 1f else circle.mass * invTotalMass

        val coefficientOfFriction = (circleBody.friction + pointBody.friction) * 0.5f
        val coefficientOfRestitution = (circleBody.restitution + pointBody.restitution) * 0.5f
        val circumference = 2 * PI.toFloat() * radius
        val rotVel = circle.rot - circle.rotLast

        if (circleBody.bodyType != STATIC)
        {
            // Accelerate rotation of circle
            val pointVelocity = xTangent * point.xVel + yTangent * point.yVel
            val rotAcc = (pointVelocity / circumference) * 2f * PI.toFloat()
            val newRotVel = rotVel - (rotAcc * circleRatio * coefficientOfFriction)
            circle.rotLast = circle.rot - newRotVel
            circle.lastRotVel = newRotVel

            // Correct circle position
            circle.x += xNormal * depth * circleRatio
            circle.y += yNormal * depth * circleRatio
        }

        if (pointBody.bodyType != STATIC)
        {
            val xV = point.xVel * (1f - coefficientOfFriction)
            val yV = point.yVel * (1f - coefficientOfFriction)
            val vnDot = xV * xNormal + yV * yNormal
            val xN = xNormal * vnDot
            val yN = yNormal * vnDot
            val xP = (xV - xN)
            val yP = (yV - yN)

            // Calculate new velocity vector
            var xVelNew = xP - xN * coefficientOfRestitution
            var yVelNew = yP - yN * coefficientOfRestitution

            // Accelerate point along tangential axis
            val pointAcc = rotVel / (2 * PI.toFloat()) * circumference
            xVelNew -= xTangent * pointAcc * coefficientOfFriction * pointRatio
            yVelNew -= yTangent * pointAcc * coefficientOfFriction * pointRatio

            // Correct point position
            if (intersection != null)
            {
                point.x = intersection.x + xNormal
                point.y = intersection.y + yNormal
            }
            else
            {
                point.x -= xNormal * depth * pointRatio
                point.y -= yNormal * depth * pointRatio
            }

            // Update velocity
            point.xLast = point.x - xVelNew
            point.yLast = point.y - yVelNew
        }

        return result.set(
            x = point.x,
            y = point.y,
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

data class ContactResult(
    var x: Float = 0f,
    var y: Float = 0f,
    var xNormal: Float = 0f,
    var yNormal: Float = 0f,
    var depth: Float = 0f
) {
    fun set(x: Float, y: Float, xNormal: Float, yNormal: Float, depth: Float): ContactResult
    {
        this.x = x
        this.y = y
        this.xNormal = xNormal
        this.yNormal = yNormal
        this.depth = depth
        return this
    }
}