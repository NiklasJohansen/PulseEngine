package no.njoh.pulseengine.core.shared.primitives

/**
 * Used for returning the hit result of a ray cast.
 * Contains the found entity and the position of where the ray hit.
 */
data class HitResult<T>(
    var entity: T,
    var xPos: Float,
    var yPos: Float,
    var distance: Float
)
