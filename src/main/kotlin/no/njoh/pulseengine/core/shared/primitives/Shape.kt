package no.njoh.pulseengine.core.shared.primitives

import org.joml.Vector2f

abstract class Shape
{
    /**
     * Returns the total amount of points the shape is built up of.
     */
    abstract fun getPointCount(): Int

    /**
     * Returns the radius of the shape if it is circular.
     */
    abstract fun getRadius(): Float?

    /**
     * Returns the coordinates of a specific shape point in world space.
     * Index higher than the last point index wraps over to zero.
     */
    abstract fun getPoint(index: Int): Vector2f

    /**
     * Sets the position of a specific shape point in world space.
     */
    abstract fun setPoint(index: Int, x: Float, y: Float)
}