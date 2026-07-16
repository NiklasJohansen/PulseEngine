package no.njoh.pulseengine.modules.physics3d.box3d

import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import no.njoh.box3d.raw.Box3DRaw.b3Body_GetContactData
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_ApplyAngularImpulse
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_ApplyForce
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_ApplyForceToCenter
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_ApplyLinearImpulse
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_ApplyLinearImpulseToCenter
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_ApplyTorque
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_GetAngularVelocity
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_GetTransform
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_GetLinearVelocity
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_IsAwake
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetAngularVelocity
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetAwake
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetLinearVelocity
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetTargetTransform
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetTransform
import no.njoh.box3d.raw.b3ContactData
import no.njoh.box3d.raw.b3Manifold
import no.njoh.box3d.raw.b3Quat
import no.njoh.box3d.raw.b3ShapeId
import no.njoh.box3d.raw.b3Transform
import no.njoh.box3d.raw.b3Vec3
import no.njoh.pulseengine.modules.physics3d.PhysicsBodyType3D
import no.njoh.pulseengine.modules.physics3d.PhysicsBody3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DWorld.Companion.setQuaternion
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DWorld.Companion.setVec3
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/** 
 * Handle to a rigid body owned by a [Box3DWorld]. 
 */
class Box3DBody internal constructor(
    override var type: PhysicsBodyType3D,
    val entityId: Long,
    val nativeBodyId: MemorySegment,
    private val tmpTransform: MemorySegment,
    private val tmpContactData: MemorySegment
) : PhysicsBody3D {
    
    private val transformAllocator = ReusingAllocator(tmpTransform)
    private val tmpPosition = b3Transform.p(tmpTransform)
    private val tmpRotation = b3Transform.q(tmpTransform)
    private val tmpVector1 = b3Quat.v(tmpRotation)
    private val vectorAllocator = ReusingAllocator(tmpVector1)
    private val contactCapacity = (tmpContactData.byteSize() / b3ContactData.sizeof()).toInt()

    val shapes = ArrayList<Box3DShape>(1)

    var isDestroyed = false; private set

    fun getTransform(dstPosition: Vector3f?, dstRotation: Quaternionf?)
    {
        requireUsable()
        b3Body_GetTransform(transformAllocator, nativeBodyId)
        dstPosition?.set(b3Vec3.x(tmpPosition), b3Vec3.y(tmpPosition), b3Vec3.z(tmpPosition))
        dstRotation?.set(b3Vec3.x(tmpVector1), b3Vec3.y(tmpVector1), b3Vec3.z(tmpVector1), b3Quat.s(tmpRotation))
    }

    fun setTransform(position: Vector3fc, rotation: Quaternionfc)
    {
        requireUsable()
        tmpPosition.setVec3(position)
        tmpRotation.setQuaternion(rotation)
        b3Body_SetTransform(nativeBodyId, tmpPosition, tmpRotation)
    }

    fun setTargetTransform(position: Vector3fc, rotation: Quaternionfc, timeStep: Float, wake: Boolean = true)
    {
        requireUsable()
        tmpPosition.setVec3(position)
        tmpRotation.setQuaternion(rotation)
        b3Body_SetTargetTransform(nativeBodyId, tmpTransform, timeStep, wake)
    }

    override fun getLinearVelocity(dstVelocity: Vector3f)
    {
        requireUsable()
        val velocity = b3Body_GetLinearVelocity(vectorAllocator, nativeBodyId)
        dstVelocity.set(b3Vec3.x(velocity), b3Vec3.y(velocity), b3Vec3.z(velocity))
    }

    override fun setLinearVelocity(velocity: Vector3fc)
    {
        requireUsable()
        b3Body_SetLinearVelocity(nativeBodyId, tmpVector1.setVec3(velocity))
    }

    override fun getAngularVelocity(dstVelocity: Vector3f)
    {
        requireUsable()
        val velocity = b3Body_GetAngularVelocity(vectorAllocator, nativeBodyId)
        dstVelocity.set(b3Vec3.x(velocity), b3Vec3.y(velocity), b3Vec3.z(velocity))
    }

    override fun setAngularVelocity(velocity: Vector3fc)
    {
        requireUsable()
        b3Body_SetAngularVelocity(nativeBodyId, tmpVector1.setVec3(velocity))
    }

    override fun applyForce(force: Vector3fc, wake: Boolean)
    {
        requireUsable()
        b3Body_ApplyForceToCenter(nativeBodyId, tmpVector1.setVec3(force), wake)
    }

    override fun applyForceAtPoint(force: Vector3fc, point: Vector3fc, wake: Boolean)
    {
        requireUsable()
        b3Body_ApplyForce(nativeBodyId, tmpVector1.setVec3(force), tmpPosition.setVec3(point), wake)
    }

    override fun applyLinearImpulse(impulse: Vector3fc, wake: Boolean)
    {
        requireUsable()
        b3Body_ApplyLinearImpulseToCenter(nativeBodyId, tmpVector1.setVec3(impulse), wake)
    }

    override fun applyLinearImpulseAtPoint(impulse: Vector3fc, point: Vector3fc, wake: Boolean)
    {
        requireUsable()
        b3Body_ApplyLinearImpulse(nativeBodyId, tmpVector1.setVec3(impulse), tmpPosition.setVec3(point), wake)
    }

    override fun applyAngularImpulse(impulse: Vector3fc, wake: Boolean)
    {
        requireUsable()
        b3Body_ApplyAngularImpulse(nativeBodyId, tmpVector1.setVec3(impulse), wake)
    }

    override fun applyTorque(torque: Vector3fc, wake: Boolean)
    {
        requireUsable()
        b3Body_ApplyTorque(nativeBodyId, tmpVector1.setVec3(torque), wake)
    }

    override fun hasContactAlong(direction: Vector3fc, minDot: Float): Boolean
    {
        requireUsable()
        val contactCount = b3Body_GetContactData(nativeBodyId, tmpContactData, contactCapacity)
        var contactIndex = 0
        while (contactIndex < contactCount)
        {
            val contact = b3ContactData.asSlice(tmpContactData, contactIndex.toLong())
            val shapeA = b3ContactData.shapeIdA(contact)
            val shapeB = b3ContactData.shapeIdB(contact)
            val isShapeA = ownsShape(shapeA)
            if (isShapeA || ownsShape(shapeB))
            {
                val manifoldCount = b3ContactData.manifoldCount(contact)
                val manifolds = b3ContactData.manifolds(contact).reinterpret(manifoldCount.toLong() * b3Manifold.sizeof())
                var manifoldIndex = 0
                while (manifoldIndex < manifoldCount)
                {
                    val manifold = b3Manifold.asSlice(manifolds, manifoldIndex.toLong())
                    if (b3Manifold.pointCount(manifold) > 0)
                    {
                        val normal = b3Manifold.normal(manifold)
                        val directionScale = if (isShapeA) 1f else -1f
                        val dot = directionScale * (
                            b3Vec3.x(normal) * direction.x() +
                            b3Vec3.y(normal) * direction.y() +
                            b3Vec3.z(normal) * direction.z()
                        )
                        if (dot >= minDot)
                            return true
                    }
                    manifoldIndex++
                }
            }
            contactIndex++
        }
        return false
    }

    override fun isAwake(): Boolean
    {
        requireUsable()
        return b3Body_IsAwake(nativeBodyId)
    }

    override fun wakeUp()
    {
        requireUsable()
        b3Body_SetAwake(nativeBodyId, true)
    }

    fun destroy()
    {
        isDestroyed = true
        shapes.clear()
    }

    private fun requireUsable()
    {
        check(!isDestroyed) { "Box3D body has been destroyed" }
    }

    private fun ownsShape(shapeId: MemorySegment): Boolean
    {
        var index = 0
        while (index < shapes.size)
        {
            val ownShapeId = shapes[index++].nativeId
            if (b3ShapeId.index1(shapeId) == b3ShapeId.index1(ownShapeId) &&
                b3ShapeId.world0(shapeId) == b3ShapeId.world0(ownShapeId) &&
                b3ShapeId.generation(shapeId) == b3ShapeId.generation(ownShapeId)
            ) {
                return true
            }
        }
        return false
    }

    private class ReusingAllocator(private val memory: MemorySegment) : SegmentAllocator
    {
        override fun allocate(byteSize: Long, byteAlignment: Long): MemorySegment
        {
            check(byteSize == memory.byteSize()) { "Native return value does not match reusable body scratch memory" }
            return memory
        }
    }
}

data class Box3DBodyDefinition(
    var type: PhysicsBodyType3D   = PhysicsBodyType3D.STATIC,
    val position: Vector3f        = Vector3f(),
    val rotation: Quaternionf     = Quaternionf(),
    val linearVelocity: Vector3f  = Vector3f(),
    val angularVelocity: Vector3f = Vector3f(),
    var linearDamping: Float      = 0f,
    var angularDamping: Float     = 0f,
    var gravityScale: Float       = 1f,
    var bullet: Boolean           = false,
    var fixedRotation: Boolean    = false
) {
    /** 
     * Hash of properties that can change after creation. Runtime pose and velocity are excluded. 
     */
    fun configurationHash(): Int
    {
        var hash = type.ordinal
        hash = 31 * hash + linearDamping.toBits()
        hash = 31 * hash + angularDamping.toBits()
        hash = 31 * hash + gravityScale.toBits()
        hash = 31 * hash + bullet.hashCode()
        hash = 31 * hash + fixedRotation.hashCode()
        return hash
    }
}