package no.njoh.pulseengine.modules.physics3d.box3d.joints

import java.lang.foreign.MemorySegment
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/** 
 * Box3D-backed revolute joint. Rotation occurs about local frame Z. 
 */
class Box3DRevoluteJoint internal constructor(
    bodyA: Box3DBody,
    bodyB: Box3DBody,
    nativeJointId: MemorySegment
) : Box3DJoint(bodyA.entityId, bodyB.entityId, bodyA, bodyB, nativeJointId)

/** 
 * Initial state for a revolute joint. Distances use meters and angles use radians. 
 */
data class Box3DRevoluteJointDefinition(
    val localPositionA: Vector3fc = Vector3f(),
    val localRotationA: Quaternionfc = Quaternionf(),
    val localPositionB: Vector3fc = Vector3f(),
    val localRotationB: Quaternionfc = Quaternionf(),
    val collideConnected: Boolean = false,
    val enableMotor: Boolean = false,
    val motorSpeed: Float = 0f,
    val maxMotorTorque: Float = 0f,
    val enableSpring: Boolean = false,
    val targetAngle: Float = 0f,
    val springHertz: Float = 4f,
    val springDampingRatio: Float = 0.7f,
    val enableLimit: Boolean = false,
    val lowerAngle: Float = -0.99f * Math.PI.toFloat(),
    val upperAngle: Float = 0.99f * Math.PI.toFloat()
) : PhysicsJointDefinition3D