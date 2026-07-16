package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Updatable
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.modules.physics3d.entities.joints.WheelJoint3D

/**
 * Simple four-wheel-drive controller using W/S for throttle and A/D for front-wheel steering.
 */
@Name("Physics Vehicle Controller (3D)")
@Icon("GEARS")
open class PhysicsVehicleController : SceneEntity(), Named, Updatable
{
    @Prop(i=0) override var name = "Vehicle Controller"
    @Prop(i=1) var enabled = true

    @EntityRef(WheelJoint3D::class)
    @Prop("Wheel Joints", i=1) var frontLeftWheelJointId = INVALID_ID

    @EntityRef(WheelJoint3D::class)
    @Prop("Wheel Joints", i=2) var frontRightWheelJointId = INVALID_ID

    @EntityRef(WheelJoint3D::class)
    @Prop("Wheel Joints", i=3) var rearLeftWheelJointId = INVALID_ID

    @EntityRef(WheelJoint3D::class)
    @Prop("Wheel Joints", i=4) var rearRightWheelJointId = INVALID_ID

    @Prop("Drive", i=1)         var driveSpeed = 25f
    @Prop("Drive", i=2, min=0f) var driveTorque = 300f
    @Prop("Drive", i=3, min=0f) var brakeTorque = 100f

    @Prop("Steering", i=1) var maxSteeringAngle = 30f

    override fun onUpdate(engine: PulseEngine) { }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (!enabled) return

        val throttle = axis(
            positivePressed = engine.input.isPressed(Key.W),
            negativePressed = engine.input.isPressed(Key.S)
        )
        val steering = axis(
            positivePressed = engine.input.isPressed(Key.A),
            negativePressed = engine.input.isPressed(Key.D)
        )

        val targetSpeed = throttle * driveSpeed
        val targetTorque = if (throttle == 0f) brakeTorque else driveTorque

        forEachWheelJoint(engine)
        {
            it.motorEnabled = true
            it.maxMotorTorque = targetTorque
            it.motorSpeed = targetSpeed
        }

        getWheelJoint(engine, frontLeftWheelJointId)?.apply() 
        { 
            steeringEnabled = true
            targetSteeringAngle = steering * maxSteeringAngle
        }

        getWheelJoint(engine, frontRightWheelJointId)?.apply() 
        {
            steeringEnabled = true
            targetSteeringAngle = steering * maxSteeringAngle
        }

        getWheelJoint(engine, rearLeftWheelJointId)?.steeringEnabled = false
        getWheelJoint(engine, rearRightWheelJointId)?.steeringEnabled = false
    }

    private inline fun forEachWheelJoint(engine: PulseEngine, action: (WheelJoint3D) -> Unit)
    {
        getWheelJoint(engine, frontLeftWheelJointId)?.let(action)
        getWheelJoint(engine, frontRightWheelJointId)?.let(action)
        getWheelJoint(engine, rearLeftWheelJointId)?.let(action)
        getWheelJoint(engine, rearRightWheelJointId)?.let(action)
    }

    private fun getWheelJoint(engine: PulseEngine, entityId: Long) =
        engine.scene.getEntityOfType<WheelJoint3D>(entityId)

    private fun axis(positivePressed: Boolean, negativePressed: Boolean) =
        (if (positivePressed) 1f else 0f) - (if (negativePressed) 1f else 0f)
}