package no.njoh.pulseengine.modules.physics3d.box3d.joints

import java.lang.foreign.MemorySegment
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_EnableSpinMotor
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_EnableSteering
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_EnableSteeringLimit
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_EnableSuspension
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_EnableSuspensionLimit
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetMaxSpinTorque
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetMaxSteeringTorque
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetSpinMotorSpeed
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetSteeringDampingRatio
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetSteeringHertz
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetSteeringLimits
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetSuspensionDampingRatio
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetSuspensionHertz
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetSuspensionLimits
import no.njoh.box3d.raw.Box3DRaw.b3WheelJoint_SetTargetSteeringAngle
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import no.njoh.pulseengine.modules.physics3d.box3d.finiteOr
import no.njoh.pulseengine.modules.physics3d.box3d.nonNegativeOr
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Box3D-backed wheel joint.
 */
class Box3DWheelJoint internal constructor(
    nativeJointId: MemorySegment,
    bodyA: Box3DBody,
    bodyB: Box3DBody
) : Box3DJoint(nativeJointId, bodyA, bodyB) {

    override fun applyDefinition(definition: PhysicsJointDefinition3D)
    {
        require(definition is Box3DWheelJointDefinition)
        definition.sanitize()
        requireUsable()

        b3WheelJoint_EnableSuspension(nativeJointId, definition.enableSuspension)
        b3WheelJoint_SetSuspensionHertz(nativeJointId, definition.suspensionHertz)
        b3WheelJoint_SetSuspensionDampingRatio(nativeJointId, definition.suspensionDampingRatio)
        b3WheelJoint_EnableSuspensionLimit(nativeJointId, definition.enableSuspensionLimit)
        b3WheelJoint_SetSuspensionLimits(nativeJointId, definition.lowerSuspensionLimit, definition.upperSuspensionLimit)
        b3WheelJoint_EnableSpinMotor(nativeJointId, definition.enableSpinMotor)
        b3WheelJoint_SetSpinMotorSpeed(nativeJointId, definition.spinSpeed)
        b3WheelJoint_SetMaxSpinTorque(nativeJointId, definition.maxSpinTorque)
        b3WheelJoint_EnableSteering(nativeJointId, definition.enableSteering)
        b3WheelJoint_SetSteeringHertz(nativeJointId, definition.steeringHertz)
        b3WheelJoint_SetSteeringDampingRatio(nativeJointId, definition.steeringDampingRatio)
        b3WheelJoint_SetMaxSteeringTorque(nativeJointId, definition.maxSteeringTorque)
        b3WheelJoint_EnableSteeringLimit(nativeJointId, definition.enableSteeringLimit)
        b3WheelJoint_SetSteeringLimits(nativeJointId, definition.lowerSteeringLimit, definition.upperSteeringLimit)
        b3WheelJoint_SetTargetSteeringAngle(nativeJointId, definition.targetSteeringAngle)
    }
}

/**
 * Initial state for a wheel joint. Distances use meters and angles use radians.
 */
data class Box3DWheelJointDefinition(
    override val worldPosA: Vector3f    = Vector3f(),
    override val worldRotA: Quaternionf = Quaternionf(),
    override val worldPosB: Vector3f    = Vector3f(),
    override val worldRotB: Quaternionf = Quaternionf(),
    override var collision: Boolean     = false,
    var enableSuspension: Boolean       = true,
    var suspensionHertz: Float          = 4f,
    var suspensionDampingRatio: Float   = 0.7f,
    var enableSuspensionLimit: Boolean  = false,
    var lowerSuspensionLimit: Float     = -0.25f,
    var upperSuspensionLimit: Float     = 0.25f,
    var enableSpinMotor: Boolean        = false,
    var maxSpinTorque: Float            = 300f,
    var spinSpeed: Float                = 0f,
    var enableSteering: Boolean         = false,
    var steeringHertz: Float            = 5f,
    var steeringDampingRatio: Float     = 0.7f,
    var targetSteeringAngle: Float      = 0f,
    var maxSteeringTorque: Float        = 300f,
    var enableSteeringLimit: Boolean    = false,
    var lowerSteeringLimit: Float       = -0.6f,
    var upperSteeringLimit: Float       = 0.6f
) : PhysicsJointDefinition3D {

    override fun sanitize()
    {
        suspensionHertz        = suspensionHertz.nonNegativeOr(4f)
        suspensionDampingRatio = suspensionDampingRatio.nonNegativeOr(0.7f)
        maxSpinTorque          = maxSpinTorque.nonNegativeOr(0f)
        spinSpeed              = spinSpeed.finiteOr(0f)
        steeringHertz          = steeringHertz.nonNegativeOr(5f)
        steeringDampingRatio   = steeringDampingRatio.nonNegativeOr(0.7f)
        targetSteeringAngle    = targetSteeringAngle.finiteOr(0f)
        maxSteeringTorque      = maxSteeringTorque.nonNegativeOr(0f)
        lowerSuspensionLimit   = lowerSuspensionLimit.finiteOr(0f)
        upperSuspensionLimit   = upperSuspensionLimit.finiteOr(0f)
        lowerSteeringLimit     = lowerSteeringLimit.finiteOr(0f)
        upperSteeringLimit     = upperSteeringLimit.finiteOr(0f)
    }
}