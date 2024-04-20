package no.njoh.pulseengine.modules.physics.bodies

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.MathUtil.atan2
import no.njoh.pulseengine.modules.physics.*
import no.njoh.pulseengine.modules.physics.BodyType.STATIC
import no.njoh.pulseengine.modules.physics.shapes.LineShape
import kotlin.math.*

interface LineBody : PhysicsBody
{
    override val shape: LineShape

    override fun init(engine: PulseEngine)
    {
        if (this is Spatial)
        {
            shape.init(x, y, width, rotation)
        }
    }

    override fun beginStep(engine: PulseEngine, timeStep: Float, gravity: Float)
    {
        if (bodyType == STATIC)
            return

        // Point 0
        val drag = 1f - drag
        val xNow0 = shape.x0
        val yNow0 = shape.y0
        val xVel0 = (xNow0 - shape.xLast0) * drag + shape.xAcc0 * timeStep * timeStep
        val yVel0 = (yNow0 - shape.yLast0) * drag + shape.yAcc0 * timeStep * timeStep
        shape.xLast0 = xNow0
        shape.yLast0 = yNow0
        shape.x0 = xNow0 + xVel0
        shape.y0 = yNow0 + yVel0
        shape.xAcc0 = 0f
        shape.yAcc0 = gravity

        // Point 1
        val xNow1 = shape.x1
        val yNow1 = shape.y1
        val xVel1 = (xNow1 - shape.xLast1) * drag + shape.xAcc1 * timeStep * timeStep
        val yVel1 = (yNow1 - shape.yLast1) * drag + shape.yAcc1 * timeStep * timeStep
        shape.xLast1 = xNow1
        shape.yLast1 = yNow1
        shape.x1 = xNow1 + xVel1
        shape.y1 = yNow1 + yVel1
        shape.xAcc1 = 0f
        shape.yAcc1 = gravity
    }

    override fun iterateStep(engine: PulseEngine, iteration: Int, totalIterations: Int, worldWidth: Int, worldHeight: Int)
    {
        if (bodyType == STATIC)
            return

        val x = (shape.x0 + shape.x1) * 0.5f
        val y = (shape.y0 + shape.y1) * 0.5f
        val xMin = min(shape.x0, shape.x1)
        val xMax = max(shape.x0, shape.x1)
        val yMin = min(shape.y0, shape.y1)
        val yMax = max(shape.y0, shape.y1)

        engine.scene.forEachEntityNearby(x, y, xMax - xMin, yMax - yMin)
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

        // Update constrains
        val stiffness = 1f
        val xDelta = shape.x0 - shape.x1
        val yDelta = shape.y0 - shape.y1
        val actualLength = sqrt(xDelta * xDelta + yDelta * yDelta)
        val targetLength = shape.length
        val deltaLength = (targetLength - actualLength) * 0.5f * stiffness
        val xChange = (xDelta / actualLength) * deltaLength
        val yChange = (yDelta / actualLength) * deltaLength
        shape.x0 += xChange
        shape.y0 += yChange
        shape.x1 -= xChange
        shape.y1 -= yChange

        // Update collisions

        if (iteration == totalIterations - 1)
        {
            onBodyUpdated()
        }
    }

    override fun render(engine: PulseEngine, surface: Surface)
    {
        surface.setDrawColor(Color.WHITE)
        surface.drawLine(shape.x0, shape.y0, shape.x1, shape.y1)
        surface.drawTexture(Texture.BLANK, shape.x0 - 5f, shape.y0 - 5f, 10f, 10f, cornerRadius = 5f)
        surface.drawTexture(Texture.BLANK, shape.x1 - 5f, shape.y1 - 5f, 10f, 10f, cornerRadius = 5f)
    }

    fun onBodyUpdated()
    {
        if (this is SceneEntity && this is Spatial)
        {
            val xDelta = shape.x1 - shape.x0
            val yDelta = shape.y1 - shape.y0
            rotation = (-atan2(yDelta, xDelta) / PI.toFloat() + 1.0f) * 180f
            x = (shape.x0 + shape.x1) * 0.5f
            y = (shape.y0 + shape.y1) * 0.5f
            set(POSITION_UPDATED and ROTATION_UPDATED)
        }
    }

    override fun wakeUp() { /* Point bodies do not sleep */ }

    override fun hasOverlappingAABB(xMin: Float, yMin: Float, xMax: Float, yMax: Float): Boolean
    {
        val xMinLine = min(shape.x0, shape.x1)
        val xMaxLine = max(shape.x0, shape.x1)
        val yMinLine = min(shape.y0, shape.y1)
        val yMaxLine = max(shape.y0, shape.y1)
        return xMin < xMaxLine && xMax > xMinLine && yMin < yMaxLine && yMax > yMinLine
    }

    override fun getMass() = shape.mass

    override fun onCollision(engine: PulseEngine, otherBody: PhysicsBody, result: ContactResult) { }
}