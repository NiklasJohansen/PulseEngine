package no.njoh.pulseengine.modules.physics3d

import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * Game-facing access to a 3D physics body.
 */
interface PhysicsBody3D
{
    val type: BodyType3D

    fun getLinearVelocity(dstVelocity: Vector3f)
    
    fun setLinearVelocity(velocity: Vector3fc)

    fun getAngularVelocity(dstVelocity: Vector3f)
    
    fun setAngularVelocity(velocity: Vector3fc)

    fun applyTorque(torque: Vector3fc, wake: Boolean = true)

    fun applyForce(force: Vector3fc, wake: Boolean = true)
    
    fun applyForceAtPoint(force: Vector3fc, point: Vector3fc, wake: Boolean = true)

    fun applyLinearImpulse(impulse: Vector3fc, wake: Boolean = true)
    
    fun applyLinearImpulseAtPoint(impulse: Vector3fc, point: Vector3fc, wake: Boolean = true)
    
    fun applyAngularImpulse(impulse: Vector3fc, wake: Boolean = true)

    fun hasContactAlong(direction: Vector3fc, minDot: Float): Boolean

    fun isAwake(): Boolean
    
    fun wakeUp()
}