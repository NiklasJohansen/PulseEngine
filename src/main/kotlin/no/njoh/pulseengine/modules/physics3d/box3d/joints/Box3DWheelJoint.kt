package no.njoh.pulseengine.modules.physics3d.box3d.joints

import java.lang.foreign.MemorySegment
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_EnableSpinMotor
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_EnableSteering
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetMaxSpinTorque
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetSpinMotorSpeed
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetTargetSteeringAngle
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.PhysicsWheelJoint3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * Box3D-backed wheel joint.
 */
class Box3DWheelJoint internal constructor(
    bodyA: Box3DBody,
    bodyB: Box3DBody,
    nativeJointId: MemorySegment
) : Box3DJoint(bodyA.entityId, bodyB.entityId, bodyA, bodyB, nativeJointId), PhysicsWheelJoint3D {

    override fun enableSpinMotor(enabled: Boolean) =
        use { b3WheelJoint_EnableSpinMotor(nativeJointId, enabled) }

    override fun setSpinMotorSpeed(radiansPerSecond: Float) =
        use { b3WheelJoint_SetSpinMotorSpeed(nativeJointId, radiansPerSecond) }

    override fun setMaxSpinTorque(torque: Float) =
        use { b3WheelJoint_SetMaxSpinTorque(nativeJointId, torque) }

    override fun enableSteering(enabled: Boolean) =
        use { b3WheelJoint_EnableSteering(nativeJointId, enabled) }

    override fun setTargetSteeringAngle(radians: Float) =
        use { b3WheelJoint_SetTargetSteeringAngle(nativeJointId, radians) }

    private inline fun use(action: () -> Unit)
    {
        requireUsable()
        action()
        wakeBodies()
    }

}

/**
 * Initial state for a wheel joint. Distances use meters and angles use radians.
 */
data class Box3DWheelJointDefinition(
    val localPositionA: Vector3fc = Vector3f(),
    val localRotationA: Quaternionfc = Quaternionf(),
    val localPositionB: Vector3fc = Vector3f(),
    val localRotationB: Quaternionfc = Quaternionf(),
    val collideConnected: Boolean = false,
    val enableSuspension: Boolean = true,
    val suspensionHertz: Float = 4f,
    val suspensionDampingRatio: Float = 0.7f,
    val enableSuspensionLimit: Boolean = false,
    val lowerSuspensionLimit: Float = -0.25f,
    val upperSuspensionLimit: Float = 0.25f,
    val enableSpinMotor: Boolean = false,
    val maxSpinTorque: Float = 300f,
    val spinSpeed: Float = 0f,
    val enableSteering: Boolean = false,
    val steeringHertz: Float = 5f,
    val steeringDampingRatio: Float = 0.7f,
    val targetSteeringAngle: Float = 0f,
    val maxSteeringTorque: Float = 300f,
    val enableSteeringLimit: Boolean = false,
    val lowerSteeringLimit: Float = -0.6f,
    val upperSteeringLimit: Float = 0.6f
) : PhysicsJointDefinition3D