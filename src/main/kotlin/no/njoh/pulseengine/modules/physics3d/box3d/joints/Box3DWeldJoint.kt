package no.njoh.pulseengine.modules.physics3d.box3d.joints

import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc
import java.lang.foreign.MemorySegment

/**
 * Box3D-backed weld joint.
 */
class Box3DWeldJoint(
    bodyA: Box3DBody,
    bodyB: Box3DBody,
    nativeJointId: MemorySegment
) : Box3DJoint(bodyA.entityId, bodyB.entityId, bodyA, bodyB, nativeJointId)

data class Box3DWeldJointDefinition(
    val localPositionA: Vector3fc = Vector3f(),
    val localRotationA: Quaternionfc = Quaternionf(),
    val localPositionB: Vector3fc = Vector3f(),
    val localRotationB: Quaternionfc = Quaternionf(),
    val collideConnected: Boolean = false,
    val linearHertz: Float = 0f,
    val linearDampingRatio: Float = 0.7f,
    val angularHertz: Float = 0f,
    val angularDampingRatio: Float = 0.7f
) : PhysicsJointDefinition3D