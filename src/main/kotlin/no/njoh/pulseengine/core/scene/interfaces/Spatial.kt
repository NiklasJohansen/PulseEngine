package no.njoh.pulseengine.core.scene.interfaces

/**
 * Gives the entity a spatial position, size and rotation.
 * Will be inserted into the [SpatialGrid] and accessible through all spatial queries.
 */
interface Spatial
{
    /**
     * X-position in world-space.
     */
    var x: Float

    /**
     * Y-position in world-space.
     */
    var y: Float

    /**
     * Z-position in world-space.
     */
    var z: Float

    /**
     * Vertical size.
     */
    var width: Float

    /**
     * Horizontal size.
     */
    var height: Float

    /**
     * Rotation in degrees.
     */
    var rotation: Float

    /**
     * Enables the entity to provide an interpolated x-position.
     */
    fun xInterpolated() = x

    /**
     * Enables the entity to provide an interpolated y-position.
     */
    fun yInterpolated() = y

    /**
     * Enables the entity to provide an interpolated rotation.
     */
    fun rotationInterpolated() = rotation
}