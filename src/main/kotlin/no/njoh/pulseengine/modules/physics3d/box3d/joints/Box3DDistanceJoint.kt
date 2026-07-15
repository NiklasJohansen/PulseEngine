package no.njoh.pulseengine.modules.physics3d.box3d.joints

import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc
import java.lang.foreign.MemorySegment

/**
 * Box3D-backed fixed-distance joint.
 */
class Box3DDistanceJoint(
    bodyA: Box3DBody,
    bodyB: Box3DBody,
    nativeJointId: MemorySegment
) : Box3DJoint(bodyA.entityId, bodyB.entityId, bodyA, bodyB, nativeJointId)

data class Box3DDistanceJointDefinition(
    val localPositionA: Vector3fc = Vector3f(),
    val localRotationA: Quaternionfc = Quaternionf(),
    val localPositionB: Vector3fc = Vector3f(),
    val localRotationB: Quaternionfc = Quaternionf(),
    val collideConnected: Boolean = false,
    val length: Float = 1f,
    val enableSpring: Boolean = false,
    val springHertz: Float = 4f,
    val springDampingRatio: Float = 0.7f,
    val enableLimit: Boolean = false,
    val minLength: Float = 0f,
    val maxLength: Float = 1f
) : PhysicsJointDefinition3D
