package no.njoh.pulseengine.modules.physics3d.entities.joints

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.PhysicsJoint3D
import no.njoh.pulseengine.modules.physics3d.PhysicsWheelJoint3D
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DWheelJointDefinition
import no.njoh.pulseengine.modules.physics3d.entities.bodies.PhysicsBodyEntity3D
import org.joml.Quaternionfc
import org.joml.Vector3fc

@Name("Wheel Joint (3D)")
@Icon("SHAPES", size = 24f, showInViewport = true)
open class WheelJoint3D : SceneEntity(), Named, Translatable3D, Rotatable3D, PhysicsJointEntity3D
{
    @Prop(i=0) override var name = "Wheel Joint"

    @Prop("Position [*P]", i=1) override var xPos = 0f; override var yPos = 0f; override var zPos = 0f
    @Prop("Rotation [*R]", i=2) override var xRot = 0f; override var yRot = 0f; override var zRot = 0f

    @EntityRef(PhysicsBodyEntity3D::class)
    @Prop("Connection", i=1) var chassisBodyId = INVALID_ID

    @EntityRef(PhysicsBodyEntity3D::class)
    @Prop("Connection", i=2) var wheelBodyId = INVALID_ID
    @Prop("Connection", i=3) var collideConnected = false
    
    @Prop("Suspension", i=1)                 var suspensionEnabled      = true
    @Prop("Suspension", i=2, min=0f)         var suspensionHertz        = 4f
    @Prop("Suspension", i=3, min=0f)         var suspensionDampingRatio = 0.7f
    @Prop("Suspension", i=4)                 var suspensionLimitEnabled = false
    @Prop("Suspension", i=5)                 var lowerSuspensionLimit   = -0.25f
    @Prop("Suspension", i=6)                 var upperSuspensionLimit   = 0.25f

    @Prop("Drive", i=1)                      var motorEnabled  = false
    @Prop("Drive", i=2)                      var motorSpeed    = 0f
    @Prop("Drive", i=3, min=0f)              var maxMotorTorque= 300f

    @Prop("Steering", i=1)                   var steeringEnabled      = false
    @Prop("Steering", i=2, min=0f)           var steeringHertz        = 5f
    @Prop("Steering", i=3, min=0f)           var steeringDampingRatio = 0.7f
    @Prop("Steering", i=4)                   var targetSteeringAngle  = 0f
    @Prop("Steering", i=5, min=0f)           var maxSteeringTorque    = 300f
    @Prop("Steering", i=6)                   var steeringLimitEnabled = false
    @Prop("Steering", i=7)                   var lowerSteeringLimit   = -35f
    @Prop("Steering", i=8)                   var upperSteeringLimit   = 35f

    @JsonIgnore
    private var runtimeJoint: PhysicsWheelJoint3D? = null

    @get:JsonIgnore override val bodyAEntityId get() = chassisBodyId
    @get:JsonIgnore override val bodyBEntityId get() = wheelBodyId
    
    /** Runtime-only motor control. This does not change the serialized initial speed. */
    fun setDriveSpeed(radiansPerSecond: Float) = runtimeJoint?.setSpinMotorSpeed(radiansPerSecond)

    fun setDriveEnabled(enabled: Boolean) = runtimeJoint?.enableSpinMotor(enabled)

    /** Runtime-only drive torque control. */
    fun setDriveTorque(torque: Float) = runtimeJoint?.setMaxSpinTorque(torque)

    /** Runtime-only steering control expressed in editor-friendly degrees. */
    fun setSteeringAngle(degrees: Float) = runtimeJoint?.setTargetSteeringAngle(degrees.toRadians())

    fun setSteeringEnabled(enabled: Boolean) = runtimeJoint?.enableSteering(enabled)

    override fun createPhysicsJointDefinition(localPosA: Vector3fc, localRotA: Quaternionfc, localPosB: Vector3fc, localRotB: Quaternionfc) = 
        Box3DWheelJointDefinition(
            localPositionA = localPosA,
            localRotationA = localRotA,
            localPositionB = localPosB,
            localRotationB = localRotB,
            collideConnected = collideConnected,
            enableSuspension = suspensionEnabled,
            suspensionHertz = suspensionHertz,
            suspensionDampingRatio = suspensionDampingRatio,
            enableSuspensionLimit = suspensionLimitEnabled,
            lowerSuspensionLimit = lowerSuspensionLimit,
            upperSuspensionLimit = upperSuspensionLimit,
            enableSpinMotor = motorEnabled,
            maxSpinTorque = maxMotorTorque,
            spinSpeed = motorSpeed,
            enableSteering = steeringEnabled,
            steeringHertz = steeringHertz,
            steeringDampingRatio = steeringDampingRatio,
            targetSteeringAngle = targetSteeringAngle.toRadians(),
            maxSteeringTorque = maxSteeringTorque,
            enableSteeringLimit = steeringLimitEnabled,
            lowerSteeringLimit = lowerSteeringLimit.toRadians(),
            upperSteeringLimit = upperSteeringLimit.toRadians()
        )

    override fun bindPhysicsJoint(joint: PhysicsJoint3D)
    {
        runtimeJoint = requireNotNull(joint as? PhysicsWheelJoint3D)
        {
            "${this::class.simpleName} requires a PhysicsWheelJoint3D runtime joint"
        }
    }

    override fun unbindPhysicsJoint()
    {
        runtimeJoint = null
    }

    override fun physicsPropertyHash(): Int
    {
        var hash = chassisBodyId.hashCode()
        hash = 31 * hash + wheelBodyId.hashCode()
        hash = 31 * hash + collideConnected.hashCode()
        hash = 31 * hash + xPos.toBits(); hash = 31 * hash + yPos.toBits(); hash = 31 * hash + zPos.toBits()
        hash = 31 * hash + xRot.toBits(); hash = 31 * hash + yRot.toBits(); hash = 31 * hash + zRot.toBits()
        hash = 31 * hash + suspensionEnabled.hashCode()
        hash = 31 * hash + suspensionHertz.toBits()
        hash = 31 * hash + suspensionDampingRatio.toBits()
        hash = 31 * hash + suspensionLimitEnabled.hashCode()
        hash = 31 * hash + lowerSuspensionLimit.toBits()
        hash = 31 * hash + upperSuspensionLimit.toBits()
        hash = 31 * hash + motorEnabled.hashCode()
        hash = 31 * hash + motorSpeed.toBits()
        hash = 31 * hash + maxMotorTorque.toBits()
        hash = 31 * hash + steeringEnabled.hashCode()
        hash = 31 * hash + steeringHertz.toBits()
        hash = 31 * hash + steeringDampingRatio.toBits()
        hash = 31 * hash + targetSteeringAngle.toBits()
        hash = 31 * hash + maxSteeringTorque.toBits()
        hash = 31 * hash + steeringLimitEnabled.hashCode()
        hash = 31 * hash + lowerSteeringLimit.toBits()
        hash = 31 * hash + upperSteeringLimit.toBits()
        return hash
    }
}