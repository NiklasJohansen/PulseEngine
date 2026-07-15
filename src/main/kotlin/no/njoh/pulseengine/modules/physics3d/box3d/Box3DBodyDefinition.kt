package no.njoh.pulseengine.modules.physics3d.box3d

import no.njoh.pulseengine.modules.physics3d.BodyType3D
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/** 
 * Initial rigid-body state. 
 * Values use meters, kilograms, and seconds. 
 */
data class Box3DBodyDefinition(
    val type: BodyType3D = BodyType3D.STATIC,
    val position: Vector3fc = Vector3f(),
    val rotation: Quaternionfc = Quaternionf(),
    val linearVelocity: Vector3fc = Vector3f(),
    val angularVelocity: Vector3fc = Vector3f(),
    val linearDamping: Float = 0f,
    val angularDamping: Float = 0f,
    val gravityScale: Float = 1f,
    val bullet: Boolean = false,
    val fixedRotation: Boolean = false
)