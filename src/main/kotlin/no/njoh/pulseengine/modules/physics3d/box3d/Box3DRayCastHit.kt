package no.njoh.pulseengine.modules.physics3d.box3d

import org.joml.Vector3f

/**
 * Closest-hit result returned by a Box3D world ray cast.
 */
data class Box3DRayCastHit(
    val shape: Box3DShape,
    val point: Vector3f,
    val normal: Vector3f,
    val fraction: Float
)