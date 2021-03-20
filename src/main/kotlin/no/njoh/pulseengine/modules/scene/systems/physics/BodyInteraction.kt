package no.njoh.pulseengine.modules.scene.systems.physics

import no.njoh.pulseengine.modules.scene.systems.physics.BodyType.STATIC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.N_POINT_FIELDS
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.X
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.X_ACC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.X_OLD
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.Y
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.Y_ACC
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.Shape.Companion.Y_OLD
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.ConvexPolygonShape
import kotlin.math.abs
import kotlin.math.sqrt

object BodyInteraction
{
    private val result = CollisionResult(0f, 0f, 0f)

    fun detectAndResolve(b1: RigidBody, b2: RigidBody): CollisionResult?
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

    private fun polygonOnPolygonCollision(aBody: RigidBody, bBody: RigidBody): CollisionResult?
    {
        var penetrationDepth = Float.MAX_VALUE
        var xCollisionNormal = 0f
        var yCollisionNormal = 0f

        lateinit var edgeBody: RigidBody
        var edgePoint0 = -1
        var edgePoint1 = -1

        var xAxis: Float
        var yAxis: Float
        val aPoints = aBody.shape.points
        val bPoints = bBody.shape.points

        val aEdgeCount = aBody.shape.nBoundaryPoints
        val bEdgeCount = bBody.shape.nBoundaryPoints
        val nEdges = aEdgeCount + bPoints.size / N_POINT_FIELDS

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
            val length = 1.0f / sqrt(xAxis * xAxis + yAxis * yAxis)
            xAxis *= length
            yAxis *= length

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

            // Stores the collision information
            val absDistance = abs(distance)
            if (absDistance < penetrationDepth)
            {
                penetrationDepth = absDistance
                xCollisionNormal = xAxis
                yCollisionNormal = yAxis
                edgeBody = if (edgeIndex < aEdgeCount) aBody else bBody
                edgePoint0 = p0
                edgePoint1 = p1
            }

            edgeIndex++
        }

        // Inverts the collision normal if it doesn't point in the direction of the pointBody
        val pointBody = if (edgeBody === aBody) bBody else aBody
        val xDelta = pointBody.shape.xCenter - edgeBody.shape.xCenter
        val yDelta = pointBody.shape.yCenter - edgeBody.shape.yCenter
        val dot = xCollisionNormal * xDelta + yCollisionNormal * yDelta
        if (dot < 0)
        {
            xCollisionNormal = 0f - xCollisionNormal
            yCollisionNormal = 0f - yCollisionNormal
        }

        // Find the point closest to the edge
        var pointIndex = -1
        var minDist = 1000000f
        pointBody.shape.forEachPoint { i ->
            val xd = this[i + X] - pointBody.shape.xCenter
            val yd = this[i + Y] - pointBody.shape.yCenter
            val dist = xCollisionNormal * xd + yCollisionNormal * yd
            if (dist < minDist)
            {
                minDist = dist
                pointIndex = i
            }
        }

        return resolveEdgePointCollision(edgeBody, pointBody, edgePoint0, edgePoint1, pointIndex, xCollisionNormal, yCollisionNormal, penetrationDepth)
    }

    private fun resolveEdgePointCollision(
        edgeBody: RigidBody,
        pointBody: RigidBody,
        edgePoint0: Int,
        edgePoint1: Int,
        pointIndex: Int,
        xCollisionNormal: Float,
        yCollisionNormal: Float,
        penetrationDepth: Float
    ): CollisionResult {
        val xCollisionVector = xCollisionNormal * penetrationDepth
        val yCollisionVector = yCollisionNormal * penetrationDepth
        val edgeShapePoints = edgeBody.shape.points
        val pointShapePoints = pointBody.shape.points
        val xEdge1 = edgeShapePoints[edgePoint0 + X]
        val yEdge1 = edgeShapePoints[edgePoint0 + Y]
        val xEdge2 = edgeShapePoints[edgePoint1 + X]
        val yEdge2 = edgeShapePoints[edgePoint1 + Y]
        val xPoint = pointShapePoints[pointIndex + X]
        val yPoint = pointShapePoints[pointIndex + Y]

        // Calculate where on the edge the collision point lies (0.0 - 1.0). If-check prevents divide by zero.
        val t =
            if (abs(xEdge1 - xEdge2) > abs(yEdge1 - yEdge2))
                (xPoint - xCollisionVector - xEdge1) / (xEdge2 - xEdge1)
            else
                (yPoint - yCollisionVector - yEdge1) / (yEdge2 - yEdge1)

        // Calculate a scaling factor that ensures that the point lies on the edge after the collision response
        val lambda = 1.0f / (t * t + (1 - t) * (1 - t))

        // Use the masses of the bodies to determine the ratio of position correction
        val invMass= 1.0f / (edgeBody.mass + pointBody.mass)
        val pointRatio = if (edgeBody.bodyType == STATIC) 1f else edgeBody.mass * invMass
        val edgeRatio = if (pointBody.bodyType == STATIC) 1f else pointBody.mass * invMass

        if (edgeBody.bodyType != STATIC)
        {
            // Correct point position
            edgeShapePoints[edgePoint0 + X] -= xCollisionVector * ((1 - t) * edgeRatio * lambda)
            edgeShapePoints[edgePoint0 + Y] -= yCollisionVector * ((1 - t) * edgeRatio * lambda)
            edgeShapePoints[edgePoint1 + X] -= xCollisionVector * (t * edgeRatio * lambda)
            edgeShapePoints[edgePoint1 + Y] -= yCollisionVector * (t * edgeRatio * lambda)

            // edgeBody.shape.applyFriction(edgePoint0, xCollisionVector, yCollisionVector, yCollisionNormal, -xCollisionNormal, edgeBody.friction * ((1 - t) * edgeRatio * lambda))
            // edgeBody.shape.applyFriction(edgePoint1, xCollisionVector, yCollisionVector, yCollisionNormal, -xCollisionNormal, edgeBody.friction * (t * edgeRatio * lambda))

            // Wake up body
            edgeBody.shape.isSleeping = false
        }

        if (pointBody.bodyType != STATIC)
        {
            // Correct point position
            pointShapePoints[pointIndex + X] += xCollisionVector * pointRatio
            pointShapePoints[pointIndex + Y] += yCollisionVector * pointRatio

            // Apply friction
            pointBody.shape.applyFriction(
                pointIndex,
                xCollisionVector,
                yCollisionVector,
                yCollisionNormal,
                -xCollisionNormal,
                pointBody.friction * pointRatio
            )

            // Wake up body
            pointBody.shape.isSleeping = false
        }

        return result.apply {
            x = xPoint + xCollisionVector * pointRatio
            y = yPoint + yCollisionVector * pointRatio
            depth = penetrationDepth
        }
    }

    private fun Shape.applyFriction(
        pointIndex: Int,
        xNormalForce: Float,
        yNormalForce: Float,
        xContactDir: Float,
        yContactDir: Float,
        frictionCoefficient: Float
    ) {
        var xMotionDir = points[pointIndex + X] - points[pointIndex + X_OLD]
        var yMotionDir = points[pointIndex + Y] - points[pointIndex + Y_OLD]

        // Only add friction impulse if point is in motion
        if (xMotionDir != 0f || yMotionDir != 0f)
        {
            // Direction of motion normalized
            val len = 1.0f / sqrt(xMotionDir * xMotionDir + yMotionDir * yMotionDir)
            xMotionDir *= len
            yMotionDir *= len

            // Dot product between motion and edge direction multiplied by friction coefficient
            val friction = (xMotionDir * xContactDir + yMotionDir * yContactDir) * frictionCoefficient

            // Apply friction impulse perpendicular to the normal force
            points[pointIndex + X_ACC] -= yNormalForce * friction
            points[pointIndex + Y_ACC] -= -xNormalForce * friction
        }
    }
}

data class CollisionResult(
    var x: Float,
    var y: Float,
    var depth: Float
)
