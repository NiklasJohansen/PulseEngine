package no.njoh.pulseengine.modules.physics3d

/** Common marker for joint definitions dispatched by the active 3D physics backend. */
interface PhysicsJointDefinition3D

/**
 * Game-facing access to a simulated joint connecting two scene entities.
 */
interface PhysicsJoint3D
{
    val bodyAEntityId: Long
    val bodyBEntityId: Long

    fun isValid(): Boolean
}

/**
 * Runtime controls used by vehicle controllers.
 * Body A is the chassis and body B is the wheel.
 */
interface PhysicsWheelJoint3D : PhysicsJoint3D
{
    fun enableSpinMotor(enabled: Boolean)
    fun setSpinMotorSpeed(radiansPerSecond: Float)
    fun setMaxSpinTorque(torque: Float)
    fun enableSteering(enabled: Boolean)
    fun setTargetSteeringAngle(radians: Float)
}