package no.njoh.pulseengine.core.shared.primitives

/**
 * Requires the implementer to define a physical shape.
 * Each point in the shape should be an absolute world coordinate.
 */
interface Physical
{
    val shape: Shape
}