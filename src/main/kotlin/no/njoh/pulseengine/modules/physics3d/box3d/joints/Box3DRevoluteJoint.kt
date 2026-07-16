package no.njoh.pulseengine.modules.physics3d.box3d.joints

import java.lang.foreign.MemorySegment
import no.njoh.box3d.raw.Box3DRaw.b3RevoluteJoint_EnableLimit
import no.njoh.box3d.raw.Box3DRaw.b3RevoluteJoint_EnableMotor
import no.njoh.box3d.raw.Box3DRaw.b3RevoluteJoint_EnableSpring
import no.njoh.box3d.raw.Box3DRaw.b3RevoluteJoint_SetLimits
import no.njoh.box3d.raw.Box3DRaw.b3RevoluteJoint_SetMaxMotorTorque
import no.njoh.box3d.raw.Box3DRaw.b3RevoluteJoint_SetMotorSpeed
import no.njoh.box3d.raw.Box3DRaw.b3RevoluteJoint_SetSpringDampingRatio
import no.njoh.box3d.raw.Box3DRaw.b3RevoluteJoint_SetSpringHertz
import no.njoh.box3d.raw.Box3DRaw.b3RevoluteJoint_SetTargetAngle
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import no.njoh.pulseengine.modules.physics3d.box3d.finiteOr
import no.njoh.pulseengine.modules.physics3d.box3d.nonNegativeOr
import org.joml.Quaternionf
import org.joml.Vector3f

private const val MAX_REVOLUTE_ANGLE = 0.99f * Math.PI.toFloat()

/** 
 * Box3D-backed revolute joint. Rotation occurs about local frame Z. 
 */
class Box3DRevoluteJoint internal constructor(
    nativeJointId: MemorySegment,
    bodyA: Box3DBody,
    bodyB: Box3DBody
) : Box3DJoint(nativeJointId, bodyA, bodyB) {

    override fun applyDefinition(definition: PhysicsJointDefinition3D)
    {
        require(definition is Box3DRevoluteJointDefinition)
        definition.sanitize()
        requireUsable()

        b3RevoluteJoint_EnableMotor(nativeJointId, definition.enableMotor)
        b3RevoluteJoint_SetMotorSpeed(nativeJointId, definition.motorSpeed)
        b3RevoluteJoint_SetMaxMotorTorque(nativeJointId, definition.maxMotorTorque)
        b3RevoluteJoint_EnableSpring(nativeJointId, definition.enableSpring)
        b3RevoluteJoint_SetTargetAngle(nativeJointId, definition.targetAngle)
        b3RevoluteJoint_SetSpringHertz(nativeJointId, definition.springHertz)
        b3RevoluteJoint_SetSpringDampingRatio(nativeJointId, definition.springDampingRatio)
        b3RevoluteJoint_EnableLimit(nativeJointId, definition.enableLimit)
        b3RevoluteJoint_SetLimits(nativeJointId, definition.lowerAngle, definition.upperAngle)
    }
}

/** 
 * Initial state for a revolute joint. Distances use meters and angles use radians. 
 */
data class Box3DRevoluteJointDefinition(
    override val worldPosA: Vector3f    = Vector3f(),
    override val worldRotA: Quaternionf = Quaternionf(),
    override val worldPosB: Vector3f    = Vector3f(),
    override val worldRotB: Quaternionf = Quaternionf(),
    override var collision: Boolean     = false,
    var enableMotor: Boolean            = false,
    var motorSpeed: Float               = 0f,
    var maxMotorTorque: Float           = 0f,
    var enableSpring: Boolean           = false,
    var targetAngle: Float              = 0f,
    var springHertz: Float              = 4f,
    var springDampingRatio: Float       = 0.7f,
    var enableLimit: Boolean            = false,
    var lowerAngle: Float               = -MAX_REVOLUTE_ANGLE,
    var upperAngle: Float               = MAX_REVOLUTE_ANGLE
) : PhysicsJointDefinition3D {

    override fun sanitize()
    {
        motorSpeed         = motorSpeed.finiteOr(0f)
        maxMotorTorque     = maxMotorTorque.nonNegativeOr(0f)
        targetAngle        = targetAngle.finiteOr(0f)
        springHertz        = springHertz.nonNegativeOr(4f)
        springDampingRatio = springDampingRatio.nonNegativeOr(0.7f)
        lowerAngle         = lowerAngle.finiteOr(-MAX_REVOLUTE_ANGLE).coerceIn(-MAX_REVOLUTE_ANGLE, MAX_REVOLUTE_ANGLE)
        upperAngle         = upperAngle.finiteOr(MAX_REVOLUTE_ANGLE).coerceIn(-MAX_REVOLUTE_ANGLE, MAX_REVOLUTE_ANGLE)
    }
}