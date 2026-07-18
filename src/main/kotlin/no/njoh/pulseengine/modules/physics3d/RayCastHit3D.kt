package no.njoh.pulseengine.modules.physics3d

import no.njoh.pulseengine.core.scene.SceneEntity
import org.joml.Vector3f

/**
 * The closest scene entity hit by a 3D physics ray cast.
 */
data class RayCastHit3D(
    var entity: SceneEntity,
    val point: Vector3f,
    val normal: Vector3f,
    var distance: Float
)