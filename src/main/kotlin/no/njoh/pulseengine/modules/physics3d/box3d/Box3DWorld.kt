package no.njoh.pulseengine.modules.physics3d.box3d

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.ValueLayout
import java.util.IdentityHashMap
import gnu.trove.list.array.TLongArrayList
import gnu.trove.map.hash.TLongObjectHashMap
import no.njoh.box3d.Box3D
import no.njoh.box3d.raw.Box3DRaw.b3CreateCapsuleShape
import no.njoh.box3d.raw.Box3DRaw.b3CreateHullShape
import no.njoh.box3d.raw.Box3DRaw.b3CreateMeshShape
import no.njoh.box3d.raw.Box3DRaw.b3CreateDistanceJoint
import no.njoh.box3d.raw.Box3DRaw.b3CreateRevoluteJoint
import no.njoh.box3d.raw.Box3DRaw.b3CreateSphereShape
import no.njoh.box3d.raw.Box3DRaw.b3CreateTransformedHullShape
import no.njoh.box3d.raw.Box3DRaw.b3CreateWeldJoint
import no.njoh.box3d.raw.Box3DRaw.b3CreateWheelJoint
import no.njoh.box3d.raw.Box3DRaw.b3DestroyShape
import no.njoh.box3d.raw.Box3DRaw.b3DestroyJoint
import no.njoh.box3d.raw.Box3DRaw.b3Joint_SetCollideConnected
import no.njoh.box3d.raw.Box3DRaw.b3Joint_SetLocalFrameA
import no.njoh.box3d.raw.Box3DRaw.b3Joint_SetLocalFrameB
import no.njoh.box3d.raw.Box3DRaw.b3Shape_EnableContactEvents
import no.njoh.box3d.raw.Box3DRaw.b3Shape_EnableHitEvents
import no.njoh.box3d.raw.Box3DRaw.b3Shape_EnableSensorEvents
import no.njoh.box3d.raw.Box3DRaw.b3Shape_SetDensity
import no.njoh.box3d.raw.Box3DRaw.b3Shape_SetFilter
import no.njoh.box3d.raw.Box3DRaw.b3Shape_SetFriction
import no.njoh.box3d.raw.Box3DRaw.b3Shape_SetRestitution
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_ApplyMassFromShapes
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetAngularDamping
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetBullet
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetGravityScale
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetLinearDamping
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetMotionLocks
import no.njoh.box3d.raw.Box3DRaw_1.b3Body_SetType
import no.njoh.box3d.raw.Box3DRaw_1.b3CreateBody
import no.njoh.box3d.raw.Box3DRaw_1.b3CreateHull
import no.njoh.box3d.raw.Box3DRaw_1.b3CreateMesh
import no.njoh.box3d.raw.Box3DRaw_1.b3CreateWorld
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultBodyDef
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultDistanceJointDef
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultRevoluteJointDef
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultShapeDef
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultWeldJointDef
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultWorldDef
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultWheelJointDef
import no.njoh.box3d.raw.Box3DRaw_1.b3DestroyBody
import no.njoh.box3d.raw.Box3DRaw_1.b3DestroyHull
import no.njoh.box3d.raw.Box3DRaw_1.b3DestroyMesh
import no.njoh.box3d.raw.Box3DRaw_1.b3DestroyWorld
import no.njoh.box3d.raw.Box3DRaw_1.b3MakeBoxHull
import no.njoh.box3d.raw.Box3DRaw_1.b3World_SetGravity
import no.njoh.box3d.raw.Box3DRaw_1.b3World_Step
import no.njoh.box3d.raw.Box3DRaw_1.b3World_GetContactEvents
import no.njoh.box3d.raw.Box3DRaw_1.b3World_GetSensorEvents
import no.njoh.box3d.raw.Box3DRaw_1.b3_dynamicBody
import no.njoh.box3d.raw.Box3DRaw_1.b3_kinematicBody
import no.njoh.box3d.raw.Box3DRaw_1.b3_staticBody
import no.njoh.box3d.raw.b3BodyDef
import no.njoh.box3d.raw.b3BoxHull
import no.njoh.box3d.raw.b3Capsule
import no.njoh.box3d.raw.b3ContactBeginTouchEvent
import no.njoh.box3d.raw.b3ContactData
import no.njoh.box3d.raw.b3ContactEndTouchEvent
import no.njoh.box3d.raw.b3ContactEvents
import no.njoh.box3d.raw.b3ContactHitEvent
import no.njoh.box3d.raw.b3DistanceJointDef
import no.njoh.box3d.raw.b3Filter
import no.njoh.box3d.raw.b3MeshDef
import no.njoh.box3d.raw.b3MotionLocks
import no.njoh.box3d.raw.b3JointDef
import no.njoh.box3d.raw.b3Quat
import no.njoh.box3d.raw.b3RevoluteJointDef
import no.njoh.box3d.raw.b3SensorBeginTouchEvent
import no.njoh.box3d.raw.b3SensorEndTouchEvent
import no.njoh.box3d.raw.b3SensorEvents
import no.njoh.box3d.raw.b3ShapeDef
import no.njoh.box3d.raw.b3ShapeId
import no.njoh.box3d.raw.b3Sphere
import no.njoh.box3d.raw.b3SurfaceMaterial
import no.njoh.box3d.raw.b3Transform
import no.njoh.box3d.raw.b3Vec3
import no.njoh.box3d.raw.b3WorldDef
import no.njoh.box3d.raw.b3WeldJointDef
import no.njoh.box3d.raw.b3WheelJointDef
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.modules.physics3d.PhysicsBodyType3D
import no.njoh.pulseengine.modules.physics3d.BoxGeometry3D
import no.njoh.pulseengine.modules.physics3d.CapsuleGeometry3D
import no.njoh.pulseengine.modules.physics3d.ConvexHullGeometry3D
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.ShapeGeometry3D
import no.njoh.pulseengine.modules.physics3d.SphereGeometry3D
import no.njoh.pulseengine.modules.physics3d.TriangleMeshGeometry3D
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DDistanceJoint
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DRevoluteJoint
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DRevoluteJointDefinition
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DJoint
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DWeldJointDefinition
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DDistanceJointDefinition
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DWeldJoint
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DWheelJoint
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DWheelJointDefinition
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * A Box3D simulation world.
 */
class Box3DWorld(gravity: Vector3fc = Vector3f(0f, -10f, 0f)) : AutoCloseable
{
    private val nativeWorldId: MemorySegment

    private val arena: Arena
    private val contactEventsAllocator: SegmentAllocator
    private val sensorEventsAllocator: SegmentAllocator

    private val bodies = mutableSetOf<Box3DBody>()
    private val joints = mutableSetOf<Box3DJoint>()
    private val cookedMeshes = IdentityHashMap<Model.CollisionMesh, MemorySegment>()
    private val shapesByNativeId = TLongObjectHashMap<Box3DShape>()
    private val destroyedShapeIds = TLongArrayList()

    private val tmpGravity: MemorySegment
    private val tmpContactEvents: MemorySegment
    private val tmpSensorEvents: MemorySegment
    private val tmpContactPoint = Vector3f()
    private val tmpContactNormal = Vector3f()
    private val tmpJointBodyPosition = Vector3f()
    private val tmpJointBodyRotation = Quaternionf()
    private val tmpJointLocalPositionA = Vector3f()
    private val tmpJointLocalRotationA = Quaternionf()
    private val tmpJointLocalPositionB = Vector3f()
    private val tmpJointLocalRotationB = Quaternionf()

    private var isClosed = false
    
    init
    {
        Box3D.load()
        arena = Arena.ofShared()

        try
        {
            val definition = b3DefaultWorldDef(arena)
            b3WorldDef.gravity(definition).setVec3(gravity)
            b3WorldDef.workerCount(definition, 1)

            nativeWorldId = b3CreateWorld(arena, definition)
            tmpGravity = b3Vec3.allocate(arena)
            tmpContactEvents = b3ContactEvents.allocate(arena)
            tmpSensorEvents = b3SensorEvents.allocate(arena)
            contactEventsAllocator = ReusingAllocator(tmpContactEvents)
            sensorEventsAllocator = ReusingAllocator(tmpSensorEvents)
        }
        catch (error: Throwable)
        {
            arena.close()
            throw error
        }
    }

    fun step(timeStep: Float, subStepCount: Int = 4)
    {
        requireOpen()
        b3World_Step(nativeWorldId, timeStep, subStepCount)
    }

    fun dispatchEvents(engine: PulseEngine, sink: Box3DEventSink)
    {
        requireOpen()
        try
        {
            dispatchContactEvents(engine, sink)
            dispatchSensorEvents(engine, sink)
        }
        finally
        {
            unregisterDestroyedShapes()
        }
    }

    fun setGravity(gravity: Vector3fc)
    {
        requireOpen()
        tmpGravity.setVec3(gravity)
        b3World_SetGravity(nativeWorldId, tmpGravity)
    }
    
    fun createBody(
        entityId: Long,
        definition: Box3DBodyDefinition,
        shapesDefinitions: List<Box3DShapeDefinition>,
        enableContactEvents: Boolean = false,
        enableSensorEvents: Boolean = false
    ): Box3DBody {

        requireOpen()
        Arena.ofConfined().use { tempArena ->

            val bodyDefinition = b3DefaultBodyDef(tempArena)
            b3BodyDef.type(bodyDefinition, definition.type.toNative())
            b3BodyDef.position(bodyDefinition).setVec3(definition.position)
            b3BodyDef.rotation(bodyDefinition).setQuaternion(definition.rotation)
            b3BodyDef.linearVelocity(bodyDefinition).setVec3(definition.linearVelocity)
            b3BodyDef.angularVelocity(bodyDefinition).setVec3(definition.angularVelocity)
            b3BodyDef.linearDamping(bodyDefinition, definition.linearDamping)
            b3BodyDef.angularDamping(bodyDefinition, definition.angularDamping)
            b3BodyDef.gravityScale(bodyDefinition, definition.gravityScale)
            b3BodyDef.isBullet(bodyDefinition, definition.continuesCollision)
            
            val motionLocks = b3BodyDef.motionLocks(bodyDefinition)
            b3MotionLocks.angularX(motionLocks, definition.lockXRotation)
            b3MotionLocks.angularY(motionLocks, definition.lockYRotation)
            b3MotionLocks.angularZ(motionLocks, definition.lockZRotation)

            val bodyId = b3CreateBody(arena, nativeWorldId, bodyDefinition)
            val body = Box3DBody(
                entityId = entityId,
                nativeBodyId = bodyId,
                type = definition.type,
                tmpTransform = b3Transform.allocate(arena),
                tmpContactData = b3ContactData.allocateArray(16, arena)
            )

            try
            {
                for (definition in shapesDefinitions)
                    body.shapes += createShape(body, definition, enableContactEvents, enableSensorEvents, tempArena)
                bodies += body
                return body
            }
            catch (error: Throwable)
            {
                unregisterShapes(body)
                b3DestroyBody(bodyId)
                body.destroy()
                throw error
            }
        }
    }

    fun synchronizeBody(
        existingBody: Box3DBody?,
        entityId: Long,
        definition: Box3DBodyDefinition,
        shapeDefinitions: List<Box3DShapeDefinition>,
        enableContactEvents: Boolean = false,
        enableSensorEvents: Boolean = false
    ): Box3DBody {
        requireOpen()

        if (existingBody == null || existingBody.isDestroyed || existingBody !in bodies || existingBody.entityId != entityId)
        {
            if (existingBody != null && !existingBody.isDestroyed && existingBody in bodies)
                destroyBody(existingBody)

            return createBody(entityId, definition, shapeDefinitions, enableContactEvents, enableSensorEvents)
        }

        updateBody(existingBody, definition)
        synchronizeShapes(existingBody, shapeDefinitions, enableContactEvents, enableSensorEvents)

        return existingBody
    }

    fun destroyBody(body: Box3DBody)
    {
        requireUsable(body)
        val attachedJoints = joints.filter { it.bodyA === body || it.bodyB === body }
        attachedJoints.forEach { it.markDestroyed() }
        joints.removeAll(attachedJoints.toSet())
        retainDestroyedShapes(body)
        b3DestroyBody(body.nativeBodyId)
        body.destroy()
        bodies.remove(body)
    }

    fun createJoint(bodyA: Box3DBody, bodyB: Box3DBody, definition: PhysicsJointDefinition3D): Box3DJoint =
        when (definition)
        {
            is Box3DWheelJointDefinition    -> createWheelJoint(bodyA, bodyB, definition)
            is Box3DRevoluteJointDefinition -> createRevoluteJoint(bodyA, bodyB, definition)
            is Box3DWeldJointDefinition     -> createWeldJoint(bodyA, bodyB, definition)
            is Box3DDistanceJointDefinition -> createDistanceJoint(bodyA, bodyB, definition)
            else -> error("Unsupported 3D joint definition: ${definition::class.qualifiedName}")
        }

    fun synchronizeJoint(joint: Box3DJoint?, bodyA: Box3DBody, bodyB: Box3DBody, definition: PhysicsJointDefinition3D): Box3DJoint
    {
        requireJointBodies(bodyA, bodyB)

        if (joint == null ||
            !joint.isValid() ||
            joint.bodyA !== bodyA ||
            joint.bodyB !== bodyB ||
            !supports(joint, definition)
        ) {
            if (joint != null && joint in joints)
            {
                if (joint.isValid())
                {
                    destroyJoint(joint)
                }
                else
                {
                    joint.markDestroyed()
                    joints.remove(joint)
                }
            }
            return createJoint(bodyA, bodyB, definition)
        }

        updateJointBase(joint, bodyA, bodyB, definition)
        joint.applyDefinition(definition)
        joint.wakeBodies()

        return joint
    }

    private fun createRevoluteJoint(bodyA: Box3DBody, bodyB: Box3DBody, definition: Box3DRevoluteJointDefinition): Box3DRevoluteJoint
    {
        requireJointBodies(bodyA, bodyB)
        definition.sanitize()

        Arena.ofConfined().use { tempArena ->
            val nativeDefinition = b3DefaultRevoluteJointDef(tempArena)

            setJointBase(b3RevoluteJointDef.base(nativeDefinition), bodyA, bodyB, definition)

            b3RevoluteJointDef.enableMotor(nativeDefinition, definition.enableMotor)
            b3RevoluteJointDef.motorSpeed(nativeDefinition, definition.motorSpeed)
            b3RevoluteJointDef.maxMotorTorque(nativeDefinition, definition.maxMotorTorque)
            b3RevoluteJointDef.enableSpring(nativeDefinition, definition.enableSpring)
            b3RevoluteJointDef.targetAngle(nativeDefinition, definition.targetAngle)
            b3RevoluteJointDef.hertz(nativeDefinition, definition.springHertz)
            b3RevoluteJointDef.dampingRatio(nativeDefinition, definition.springDampingRatio)
            b3RevoluteJointDef.enableLimit(nativeDefinition, definition.enableLimit)
            b3RevoluteJointDef.lowerAngle(nativeDefinition, definition.lowerAngle)
            b3RevoluteJointDef.upperAngle(nativeDefinition, definition.upperAngle)

            val nativeJointId = b3CreateRevoluteJoint(arena, nativeWorldId, nativeDefinition)
            return registerJoint(Box3DRevoluteJoint(nativeJointId, bodyA, bodyB), definition)
        }
    }

    private fun createWeldJoint(bodyA: Box3DBody, bodyB: Box3DBody, definition: Box3DWeldJointDefinition): Box3DWeldJoint
    {
        requireJointBodies(bodyA, bodyB)
        definition.sanitize()

        Arena.ofConfined().use { tempArena ->
            val nativeDefinition = b3DefaultWeldJointDef(tempArena)

            setJointBase(b3WeldJointDef.base(nativeDefinition), bodyA, bodyB, definition)

            b3WeldJointDef.linearHertz(nativeDefinition, definition.linearHertz)
            b3WeldJointDef.linearDampingRatio(nativeDefinition, definition.linearDampingRatio)
            b3WeldJointDef.angularHertz(nativeDefinition, definition.angularHertz)
            b3WeldJointDef.angularDampingRatio(nativeDefinition, definition.angularDampingRatio)

            val nativeJointId = b3CreateWeldJoint(arena, nativeWorldId, nativeDefinition)
            return registerJoint(Box3DWeldJoint(nativeJointId, bodyA, bodyB), definition)
        }
    }

    private fun createDistanceJoint(bodyA: Box3DBody, bodyB: Box3DBody, definition: Box3DDistanceJointDefinition): Box3DDistanceJoint
    {
        requireJointBodies(bodyA, bodyB)
        definition.sanitize()

        Arena.ofConfined().use { tempArena ->
            val nativeDefinition = b3DefaultDistanceJointDef(tempArena)

            setJointBase(b3DistanceJointDef.base(nativeDefinition), bodyA, bodyB, definition)

            b3DistanceJointDef.length(nativeDefinition, definition.length)
            b3DistanceJointDef.enableSpring(nativeDefinition, definition.enableSpring)
            b3DistanceJointDef.hertz(nativeDefinition, definition.springHertz)
            b3DistanceJointDef.dampingRatio(nativeDefinition, definition.springDampingRatio)
            b3DistanceJointDef.enableLimit(nativeDefinition, definition.enableLimit)
            b3DistanceJointDef.minLength(nativeDefinition, definition.minLength)
            b3DistanceJointDef.maxLength(nativeDefinition, definition.maxLength)

            val nativeJointId = b3CreateDistanceJoint(arena, nativeWorldId, nativeDefinition)
            return registerJoint(Box3DDistanceJoint(nativeJointId, bodyA, bodyB), definition)
        }
    }

    private fun createWheelJoint(bodyA: Box3DBody, bodyB: Box3DBody, definition: Box3DWheelJointDefinition): Box3DWheelJoint
    {
        requireJointBodies(bodyA, bodyB)
        definition.sanitize()

        Arena.ofConfined().use { tempArena ->
            val nativeDefinition = b3DefaultWheelJointDef(tempArena)

            setJointBase(b3WheelJointDef.base(nativeDefinition), bodyA, bodyB, definition)

            b3WheelJointDef.enableSuspensionSpring(nativeDefinition, definition.enableSuspension)
            b3WheelJointDef.suspensionHertz(nativeDefinition, definition.suspensionHertz)
            b3WheelJointDef.suspensionDampingRatio(nativeDefinition, definition.suspensionDampingRatio)
            b3WheelJointDef.enableSuspensionLimit(nativeDefinition, definition.enableSuspensionLimit)
            b3WheelJointDef.lowerSuspensionLimit(nativeDefinition, definition.lowerSuspensionLimit)
            b3WheelJointDef.upperSuspensionLimit(nativeDefinition, definition.upperSuspensionLimit)
            b3WheelJointDef.enableSpinMotor(nativeDefinition, definition.enableSpinMotor)
            b3WheelJointDef.maxSpinTorque(nativeDefinition, definition.maxSpinTorque)
            b3WheelJointDef.spinSpeed(nativeDefinition, definition.spinSpeed)
            b3WheelJointDef.enableSteering(nativeDefinition, definition.enableSteering)
            b3WheelJointDef.steeringHertz(nativeDefinition, definition.steeringHertz)
            b3WheelJointDef.steeringDampingRatio(nativeDefinition, definition.steeringDampingRatio)
            b3WheelJointDef.targetSteeringAngle(nativeDefinition, definition.targetSteeringAngle)
            b3WheelJointDef.maxSteeringTorque(nativeDefinition, definition.maxSteeringTorque)
            b3WheelJointDef.enableSteeringLimit(nativeDefinition, definition.enableSteeringLimit)
            b3WheelJointDef.lowerSteeringLimit(nativeDefinition, definition.lowerSteeringLimit)
            b3WheelJointDef.upperSteeringLimit(nativeDefinition, definition.upperSteeringLimit)

            val nativeJointId = b3CreateWheelJoint(arena, nativeWorldId, nativeDefinition)
            return registerJoint(Box3DWheelJoint(nativeJointId, bodyA, bodyB), definition)
        }
    }

    fun destroyJoint(joint: Box3DJoint, wakeAttached: Boolean = false)
    {
        requireOpen()
        check(!joint.isDestroyed && joint in joints) { "Physics joint has been destroyed or belongs to another world" }
        b3DestroyJoint(joint.nativeJointId, wakeAttached)
        joint.markDestroyed()
        joints.remove(joint)
    }

    override fun close()
    {
        if (isClosed) return

        try
        {
            b3DestroyWorld(nativeWorldId)
            joints.forEach { it.markDestroyed() }
            joints.clear()
            bodies.forEach { it.destroy() }
            bodies.clear()
            shapesByNativeId.clear()
            destroyedShapeIds.clear()
            cookedMeshes.forEach { (_, mesh) -> b3DestroyMesh(mesh) }
            cookedMeshes.clear()
        }
        finally
        {
            isClosed = true
            arena.close()
        }
    }

    private fun requireUsable(body: Box3DBody)
    {
        requireOpen()
        check(!body.isDestroyed && body in bodies) { "Physics body has been destroyed or belongs to another world" }
    }

    private fun requireJointBodies(bodyA: Box3DBody, bodyB: Box3DBody)
    {
        requireUsable(bodyA)
        requireUsable(bodyB)
        require(bodyA !== bodyB) { "A joint must connect two different bodies" }
    }

    private fun supports(joint: Box3DJoint, definition: PhysicsJointDefinition3D) = when (joint)
    {
        is Box3DWheelJoint    ->  definition is Box3DWheelJointDefinition
        is Box3DRevoluteJoint ->  definition is Box3DRevoluteJointDefinition
        is Box3DWeldJoint     ->  definition is Box3DWeldJointDefinition
        is Box3DDistanceJoint ->  definition is Box3DDistanceJointDefinition
        else -> false 
    }

    private fun updateJointBase(
        joint: Box3DJoint,
        bodyA: Box3DBody,
        bodyB: Box3DBody,
        definition: PhysicsJointDefinition3D
    ) {
        val frameHash = jointFrameHash(definition)
        if (joint.appliedFrameHash != frameHash)
        {
            resolveJointFrames(bodyA, bodyB, definition)
            Arena.ofConfined().use { tempArena ->
                val frameA = b3Transform.allocate(tempArena)
                b3Transform.p(frameA).setVec3(tmpJointLocalPositionA)
                b3Transform.q(frameA).setQuaternion(tmpJointLocalRotationA)
                b3Joint_SetLocalFrameA(joint.nativeJointId, frameA)

                val frameB = b3Transform.allocate(tempArena)
                b3Transform.p(frameB).setVec3(tmpJointLocalPositionB)
                b3Transform.q(frameB).setQuaternion(tmpJointLocalRotationB)
                b3Joint_SetLocalFrameB(joint.nativeJointId, frameB)
            }
            joint.appliedFrameHash = frameHash
        }

        b3Joint_SetCollideConnected(joint.nativeJointId, definition.collision)
    }

    private fun <T : Box3DJoint> registerJoint(joint: T, definition: PhysicsJointDefinition3D): T
    {
        joint.appliedFrameHash = jointFrameHash(definition)
        joints += joint
        return joint
    }

    private fun jointFrameHash(definition: PhysicsJointDefinition3D): Int
    {
        var hash = definition.worldPosA.hashCode()
        hash = 31 * hash + definition.worldRotA.hashCode()
        hash = 31 * hash + definition.worldPosB.hashCode()
        hash = 31 * hash + definition.worldRotB.hashCode()
        return hash
    }

    private fun setJointBase(
        baseDefinition: MemorySegment,
        bodyA: Box3DBody,
        bodyB: Box3DBody,
        definition: PhysicsJointDefinition3D
    ) {
        resolveJointFrames(bodyA, bodyB, definition)
        b3JointDef.bodyIdA(baseDefinition, bodyA.nativeBodyId)
        b3JointDef.bodyIdB(baseDefinition, bodyB.nativeBodyId)
        b3JointDef.collideConnected(baseDefinition, definition.collision)

        val frameA = b3JointDef.localFrameA(baseDefinition)
        b3Transform.p(frameA).setVec3(tmpJointLocalPositionA)
        b3Transform.q(frameA).setQuaternion(tmpJointLocalRotationA)

        val frameB = b3JointDef.localFrameB(baseDefinition)
        b3Transform.p(frameB).setVec3(tmpJointLocalPositionB)
        b3Transform.q(frameB).setQuaternion(tmpJointLocalRotationB)
    }

    private fun resolveJointFrames(bodyA: Box3DBody, bodyB: Box3DBody, definition: PhysicsJointDefinition3D) 
    {
        bodyA.getTransform(tmpJointBodyPosition, tmpJointBodyRotation)
        tmpJointLocalRotationA.set(tmpJointBodyRotation).conjugate().normalize()
        tmpJointLocalPositionA.set(definition.worldPosA).sub(tmpJointBodyPosition)
        tmpJointLocalRotationA.transform(tmpJointLocalPositionA)
        tmpJointLocalRotationA.mul(definition.worldRotA).normalize()

        bodyB.getTransform(tmpJointBodyPosition, tmpJointBodyRotation)
        tmpJointLocalRotationB.set(tmpJointBodyRotation).conjugate().normalize()
        tmpJointLocalPositionB.set(definition.worldPosB).sub(tmpJointBodyPosition)
        tmpJointLocalRotationB.transform(tmpJointLocalPositionB)
        tmpJointLocalRotationB.mul(definition.worldRotB).normalize()
    }

    private fun createShape(
        body: Box3DBody,
        definition: Box3DShapeDefinition,
        enableContactEvents: Boolean,
        enableSensorEvents: Boolean,
        tmpArena: Arena
    ): Box3DShape {

        val shapeDefinition = b3DefaultShapeDef(tmpArena)
        b3ShapeDef.density(shapeDefinition, definition.density)
        b3ShapeDef.isSensor(shapeDefinition, definition.sensor)
        b3ShapeDef.enableContactEvents(shapeDefinition, definition.enableContactEvents || enableContactEvents)
        b3ShapeDef.enableSensorEvents(shapeDefinition, definition.enableSensorEvents || enableSensorEvents)
        b3ShapeDef.enableHitEvents(shapeDefinition, definition.enableHitEvents || enableContactEvents)

        val material = b3ShapeDef.baseMaterial(shapeDefinition)
        b3SurfaceMaterial.friction(material, definition.friction)
        b3SurfaceMaterial.restitution(material, definition.restitution)

        val filter = b3ShapeDef.filter(shapeDefinition)
        b3Filter.categoryBits(filter, definition.categoryBits)
        b3Filter.maskBits(filter, definition.maskBits)
        b3Filter.groupIndex(filter, definition.groupIndex)

        val geometry = definition.geometry
        val shapeId = when (geometry)
        {
            is TriangleMeshGeometry3D if body.type != PhysicsBodyType3D.STATIC ->
            {
                Logger.warn { "Triangle mesh collision '${geometry.mesh.name}' is only supported on static bodies, using its bounding box for ${body.type} body ${body.entityId}" }
                createTriangleMeshFallbackBoxShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
            }
            is BoxGeometry3D -> createBoxShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
            is SphereGeometry3D -> createSphereShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
            is CapsuleGeometry3D -> createCapsuleShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
            is ConvexHullGeometry3D -> createConvexHullShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
            is TriangleMeshGeometry3D -> createTriangleMeshShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
        }

        val shape = Box3DShape(shapeId, body, effectiveGeometryHash(body.type, geometry), definition.sensor)
        shapesByNativeId.put(shapeKey(shapeId), shape)
        return shape
    }

    private fun updateBody(body: Box3DBody, definition: Box3DBodyDefinition)
    {
        b3Body_SetType(body.nativeBodyId, definition.type.toNative())
        b3Body_SetLinearDamping(body.nativeBodyId, definition.linearDamping)
        b3Body_SetAngularDamping(body.nativeBodyId, definition.angularDamping)
        b3Body_SetGravityScale(body.nativeBodyId, definition.gravityScale)
        b3Body_SetBullet(body.nativeBodyId, definition.continuesCollision)

        Arena.ofConfined().use { tempArena ->
            val motionLocks = b3MotionLocks.allocate(tempArena)
            b3MotionLocks.angularX(motionLocks, definition.lockXRotation)
            b3MotionLocks.angularY(motionLocks, definition.lockYRotation)
            b3MotionLocks.angularZ(motionLocks, definition.lockZRotation)
            b3Body_SetMotionLocks(body.nativeBodyId, motionLocks)
        }

        body.type = definition.type
    }

    private fun synchronizeShapes(
        body: Box3DBody,
        definitions: List<Box3DShapeDefinition>,
        enableContactEvents: Boolean,
        enableSensorEvents: Boolean
    ) {
        val replaceShapes = body.shapes.size != definitions.size || definitions.indices.any { index ->
            val shape = body.shapes[index]
            val definition = definitions[index]
            shape.geometryHash != effectiveGeometryHash(body.type, definition.geometry) || shape.sensor != definition.sensor
        }

        if (replaceShapes)
        {
            retainDestroyedShapes(body)
            body.shapes.forEachFast { b3DestroyShape(it.nativeId, false) }
            body.shapes.clear()

            Arena.ofConfined().use { tempArena ->
                for (definition in definitions)
                    body.shapes += createShape(body, definition, enableContactEvents, enableSensorEvents, tempArena)
            }
  
            b3Body_ApplyMassFromShapes(body.nativeBodyId)
            return
        }

        Arena.ofConfined().use { tempArena ->
            val filter = b3Filter.allocate(tempArena)
            definitions.forEachIndexed { index, definition ->
                val shape = body.shapes[index]
                b3Shape_SetDensity(shape.nativeId, definition.density, false)
                b3Shape_SetFriction(shape.nativeId, definition.friction)
                b3Shape_SetRestitution(shape.nativeId, definition.restitution)
                b3Filter.categoryBits(filter, definition.categoryBits)
                b3Filter.maskBits(filter, definition.maskBits)
                b3Filter.groupIndex(filter, definition.groupIndex)
                b3Shape_SetFilter(shape.nativeId, filter, true)
                b3Shape_EnableContactEvents(shape.nativeId, definition.enableContactEvents || enableContactEvents)
                b3Shape_EnableSensorEvents(shape.nativeId, definition.enableSensorEvents || enableSensorEvents)
                b3Shape_EnableHitEvents(shape.nativeId, definition.enableHitEvents || enableContactEvents)
            }
        }

        b3Body_ApplyMassFromShapes(body.nativeBodyId)
    }

    private fun createBoxShape(bodyId: MemorySegment, shapeDefinition: MemorySegment, geometry: BoxGeometry3D, tmpArena: Arena): MemorySegment
    {
        val hull = b3MakeBoxHull(
            tmpArena, 
            geometry.size.x() * 0.5f, 
            geometry.size.y() * 0.5f, 
            geometry.size.z() * 0.5f
        )

        val hasTransform = 
            geometry.center.lengthSquared() != 0f ||
            geometry.rotation.x() != 0f || 
            geometry.rotation.y() != 0f || 
            geometry.rotation.z() != 0f || 
            geometry.rotation.w() != 1f
        
        if (!hasTransform)
            return b3CreateHullShape(arena, bodyId, shapeDefinition, b3BoxHull.base(hull))

        val transform = b3Transform.allocate(tmpArena)
        b3Transform.p(transform).setVec3(geometry.center)
        b3Transform.q(transform).setQuaternion(geometry.rotation)

        val scale = b3Vec3.allocate(tmpArena).setVec3(UNIT_SCALE)

        return b3CreateTransformedHullShape(arena, bodyId, shapeDefinition, b3BoxHull.base(hull), transform, scale)
    }

    private fun createSphereShape(bodyId: MemorySegment, shapeDefinition: MemorySegment, geometry: SphereGeometry3D, tmpArena: Arena): MemorySegment
    {
        val sphere = b3Sphere.allocate(tmpArena)
        b3Sphere.center(sphere).setVec3(geometry.center)
        b3Sphere.radius(sphere, geometry.radius)
        return b3CreateSphereShape(arena, bodyId, shapeDefinition, sphere)
    }

    private fun createCapsuleShape(bodyId: MemorySegment, shapeDefinition: MemorySegment, geometry: CapsuleGeometry3D, tmpArena: Arena): MemorySegment
    {
        val capsule = b3Capsule.allocate(tmpArena)
        b3Capsule.center1(capsule).setVec3(geometry.point1)
        b3Capsule.center2(capsule).setVec3(geometry.point2)
        b3Capsule.radius(capsule, geometry.radius)
        return b3CreateCapsuleShape(arena, bodyId, shapeDefinition, capsule)
    }

    private fun createConvexHullShape(bodyId: MemorySegment, shapeDefinition: MemorySegment, geometry: ConvexHullGeometry3D, tmpArena: Arena): MemorySegment
    {
        val points = createTransformedVertices(geometry.mesh, tmpArena)
        val pointCount = geometry.mesh.vertices.size / 3
        val hull = b3CreateHull(points, pointCount, pointCount)
        check(hull != MemorySegment.NULL) { "Could not cook convex hull '${geometry.mesh.name}'" }

        try
        {
            val transform = b3Transform.allocate(tmpArena)
            b3Transform.p(transform).setVec3(ZERO)
            b3Transform.q(transform).setQuaternion(IDENTITY_ROTATION)
            val scale = b3Vec3.allocate(tmpArena).setVec3(geometry.scale)
            return b3CreateTransformedHullShape(arena, bodyId, shapeDefinition, hull, transform, scale)
        }
        finally
        {
            b3DestroyHull(hull)
        }
    }

    private fun createTriangleMeshShape(bodyId: MemorySegment, shapeDefinition: MemorySegment, geometry: TriangleMeshGeometry3D, tmpArena: Arena): MemorySegment
    {
        val cookedMesh = cookedMeshes[geometry.mesh] ?: cookMesh(geometry.mesh, tmpArena).also { cookedMeshes[geometry.mesh] = it }
        val scale = b3Vec3.allocate(tmpArena).setVec3(geometry.scale)
        return b3CreateMeshShape(arena, bodyId, shapeDefinition, cookedMesh, scale)
    }

    private fun createTriangleMeshFallbackBoxShape(
        bodyId: MemorySegment,
        shapeDefinition: MemorySegment,
        geometry: TriangleMeshGeometry3D,
        tmpArena: Arena
    ): MemorySegment {
        
        val vertices = geometry.mesh.vertices
        val transform = geometry.mesh.transform
        val scale = geometry.scale
        var xMin = Float.POSITIVE_INFINITY
        var yMin = Float.POSITIVE_INFINITY
        var zMin = Float.POSITIVE_INFINITY
        var xMax = Float.NEGATIVE_INFINITY
        var yMax = Float.NEGATIVE_INFINITY
        var zMax = Float.NEGATIVE_INFINITY

        var source = 0
        while (source + 2 < vertices.size)
        {
            val x = vertices[source++]
            val y = vertices[source++]
            val z = vertices[source++]
            val xTransformed = (transform.m00() * x + transform.m10() * y + transform.m20() * z + transform.m30()) * scale.x
            val yTransformed = (transform.m01() * x + transform.m11() * y + transform.m21() * z + transform.m31()) * scale.y
            val zTransformed = (transform.m02() * x + transform.m12() * y + transform.m22() * z + transform.m32()) * scale.z

            if (xTransformed < xMin) xMin = xTransformed
            if (yTransformed < yMin) yMin = yTransformed
            if (zTransformed < zMin) zMin = zTransformed
            if (xTransformed > xMax) xMax = xTransformed
            if (yTransformed > yMax) yMax = yTransformed
            if (zTransformed > zMax) zMax = zTransformed
        }

        if (!xMin.isFinite() || !yMin.isFinite() || !zMin.isFinite() || !xMax.isFinite() || !yMax.isFinite() || !zMax.isFinite())
        {
            xMin = -0.5f
            yMin = -0.5f
            zMin = -0.5f
            xMax = 0.5f
            yMax = 0.5f
            zMax = 0.5f
        }

        val xCenter   =  (xMin + xMax) * 0.5f
        val yCenter   =  (yMin + yMax) * 0.5f
        val zCenter   =  (zMin + zMax) * 0.5f
        val xHalfSize = ((xMax - xMin) * 0.5f).coerceAtLeast(MIN_FALLBACK_BOX_HALF_SIZE)
        val yHalfSize = ((yMax - yMin) * 0.5f).coerceAtLeast(MIN_FALLBACK_BOX_HALF_SIZE)
        val zHalfSize = ((zMax - zMin) * 0.5f).coerceAtLeast(MIN_FALLBACK_BOX_HALF_SIZE)
        val hull = b3MakeBoxHull(tmpArena, xHalfSize, yHalfSize, zHalfSize)

        if (xCenter == 0f && yCenter == 0f && zCenter == 0f)
            return b3CreateHullShape(arena, bodyId, shapeDefinition, b3BoxHull.base(hull))

        val boxTransform = b3Transform.allocate(tmpArena)
        b3Vec3.x(b3Transform.p(boxTransform), xCenter)
        b3Vec3.y(b3Transform.p(boxTransform), yCenter)
        b3Vec3.z(b3Transform.p(boxTransform), zCenter)
        b3Transform.q(boxTransform).setQuaternion(IDENTITY_ROTATION)
 
        val unitScale = b3Vec3.allocate(tmpArena).setVec3(UNIT_SCALE)

        return b3CreateTransformedHullShape(arena, bodyId, shapeDefinition, b3BoxHull.base(hull), boxTransform, unitScale)
    }

    private fun cookMesh(mesh: Model.CollisionMesh, tmpArena: Arena): MemorySegment
    {
        val vertexCount = mesh.vertices.size / 3
        val triangleCount = mesh.indices.size / 3
        val nativeVertices = createTransformedVertices(mesh, tmpArena)
        val nativeIndices = tmpArena.allocate(ValueLayout.JAVA_INT, mesh.indices.size.toLong())
        val reverseWinding = mesh.transform.determinant3x3() < 0f
        var index = 0
        while (index < mesh.indices.size)
        {
            nativeIndices.setAtIndex(ValueLayout.JAVA_INT, index.toLong(), mesh.indices[index])
            nativeIndices.setAtIndex(
                ValueLayout.JAVA_INT,
                (index + 1).toLong(),
                mesh.indices[index + if (reverseWinding) 2 else 1]
            )
            nativeIndices.setAtIndex(
                ValueLayout.JAVA_INT,
                (index + 2).toLong(),
                mesh.indices[index + if (reverseWinding) 1 else 2]
            )
            index += 3
        }

        val meshDefinition = b3MeshDef.allocate(tmpArena)
        b3MeshDef.vertices(meshDefinition, nativeVertices)
        b3MeshDef.indices(meshDefinition, nativeIndices)
        b3MeshDef.materialIndices(meshDefinition, MemorySegment.NULL)
        b3MeshDef.weldTolerance(meshDefinition, 0.0001f)
        b3MeshDef.vertexCount(meshDefinition, vertexCount)
        b3MeshDef.triangleCount(meshDefinition, triangleCount)
        b3MeshDef.weldVertices(meshDefinition, true)
        b3MeshDef.useMedianSplit(meshDefinition, false)
        b3MeshDef.identifyEdges(meshDefinition, true)

        val cookedMesh = b3CreateMesh(meshDefinition, MemorySegment.NULL, 0)
        check(cookedMesh != MemorySegment.NULL) { "Could not cook triangle mesh '${mesh.name}'" }
        return cookedMesh
    }

    private fun dispatchContactEvents(engine: PulseEngine, sink: Box3DEventSink)
    {
        val events = b3World_GetContactEvents(contactEventsAllocator, nativeWorldId)

        val beginCount = b3ContactEvents.beginCount(events)
        if (beginCount > 0)
        {
            val beginEvents = b3ContactEvents.beginEvents(events).reinterpret(beginCount.toLong() * b3ContactBeginTouchEvent.sizeof())
            var index = 0
            while (index < beginCount)
            {
                val event = b3ContactBeginTouchEvent.asSlice(beginEvents, index.toLong())
                val shapeA = findShape(b3ContactBeginTouchEvent.shapeIdA(event))
                val shapeB = findShape(b3ContactBeginTouchEvent.shapeIdB(event))
                if (shapeA != null && shapeB != null)
                    sink.onContactStarted(engine, shapeA, shapeB)
                index++
            }
        }

        val endCount = b3ContactEvents.endCount(events)
        if (endCount > 0)
        {
            val endEvents = b3ContactEvents.endEvents(events).reinterpret(endCount.toLong() * b3ContactEndTouchEvent.sizeof())
            var index = 0
            while (index < endCount)
            {
                val event = b3ContactEndTouchEvent.asSlice(endEvents, index.toLong())
                val shapeA = findShape(b3ContactEndTouchEvent.shapeIdA(event))
                val shapeB = findShape(b3ContactEndTouchEvent.shapeIdB(event))
                if (shapeA != null && shapeB != null)
                    sink.onContactEnded(engine, shapeA, shapeB)
                index++
            }
        }

        val hitCount = b3ContactEvents.hitCount(events)
        if (hitCount > 0)
        {
            val hitEvents = b3ContactEvents.hitEvents(events).reinterpret(hitCount.toLong() * b3ContactHitEvent.sizeof())
            var index = 0
            while (index < hitCount)
            {
                val event = b3ContactHitEvent.asSlice(hitEvents, index.toLong())
                val shapeA = findShape(b3ContactHitEvent.shapeIdA(event))
                val shapeB = findShape(b3ContactHitEvent.shapeIdB(event))
                if (shapeA != null && shapeB != null)
                {
                    val point = b3ContactHitEvent.point(event)
                    val normal = b3ContactHitEvent.normal(event)
                    sink.onContactHit(
                        engine = engine,
                        shapeA = shapeA,
                        shapeB = shapeB,
                        point = tmpContactPoint.set(b3Vec3.x(point), b3Vec3.y(point), b3Vec3.z(point)),
                        normal = tmpContactNormal.set(b3Vec3.x(normal), b3Vec3.y(normal), b3Vec3.z(normal)),
                        approachSpeed = b3ContactHitEvent.approachSpeed(event)
                    )
                }
                index++
            }
        }
    }

    private fun dispatchSensorEvents(engine: PulseEngine, sink: Box3DEventSink)
    {
        val events = b3World_GetSensorEvents(sensorEventsAllocator, nativeWorldId)

        val beginCount = b3SensorEvents.beginCount(events)
        if (beginCount > 0)
        {
            val beginEvents = b3SensorEvents.beginEvents(events).reinterpret(beginCount.toLong() * b3SensorBeginTouchEvent.sizeof())
            var index = 0
            while (index < beginCount)
            {
                val event = b3SensorBeginTouchEvent.asSlice(beginEvents, index.toLong())
                val sensorShape = findShape(b3SensorBeginTouchEvent.sensorShapeId(event))
                val visitorShape = findShape(b3SensorBeginTouchEvent.visitorShapeId(event))
                if (sensorShape != null && visitorShape != null)
                    sink.onSensorEntered(engine, sensorShape, visitorShape)
                index++
            }
        }

        val endCount = b3SensorEvents.endCount(events)
        if (endCount > 0)
        {
            val endEvents = b3SensorEvents.endEvents(events).reinterpret(endCount.toLong() * b3SensorEndTouchEvent.sizeof())
            var index = 0
            while (index < endCount)
            {
                val event = b3SensorEndTouchEvent.asSlice(endEvents, index.toLong())
                val sensorShape = findShape(b3SensorEndTouchEvent.sensorShapeId(event))
                val visitorShape = findShape(b3SensorEndTouchEvent.visitorShapeId(event))
                if (sensorShape != null && visitorShape != null)
                    sink.onSensorExited(engine, sensorShape, visitorShape)
                index++
            }
        }
    }

    private fun unregisterShapes(body: Box3DBody)
    {
        var index = 0
        while (index < body.shapes.size)
        {
            shapesByNativeId.remove(shapeKey(body.shapes[index].nativeId))
            index++
        }
    }

    private fun retainDestroyedShapes(body: Box3DBody)
    {
        var index = 0
        while (index < body.shapes.size)
        {
            destroyedShapeIds.add(shapeKey(body.shapes[index].nativeId))
            index++
        }
    }

    private fun unregisterDestroyedShapes()
    {
        var index = 0
        while (index < destroyedShapeIds.size())
        {
            shapesByNativeId.remove(destroyedShapeIds[index])
            index++
        }
        destroyedShapeIds.clear()
    }

    private fun findShape(shapeId: MemorySegment) = shapesByNativeId[shapeKey(shapeId)]

    private fun shapeKey(shapeId: MemorySegment): Long =
        (b3ShapeId.index1(shapeId).toLong() and 0xffffffffL) or
        ((b3ShapeId.world0(shapeId).toLong() and 0xffffL) shl 32) or
        ((b3ShapeId.generation(shapeId).toLong() and 0xffffL) shl 48)

    private fun createTransformedVertices(mesh: Model.CollisionMesh, tmpArena: Arena): MemorySegment
    {
        val vertexCount = mesh.vertices.size / 3
        val result = b3Vec3.allocateArray(vertexCount.toLong(), tmpArena)
        val transform = mesh.transform
        var source = 0
        for (vertexIndex in 0 until vertexCount)
        {
            val x = mesh.vertices[source++]
            val y = mesh.vertices[source++]
            val z = mesh.vertices[source++]
            val destination = b3Vec3.asSlice(result, vertexIndex.toLong())
            b3Vec3.x(destination, transform.m00() * x + transform.m10() * y + transform.m20() * z + transform.m30())
            b3Vec3.y(destination, transform.m01() * x + transform.m11() * y + transform.m21() * z + transform.m31())
            b3Vec3.z(destination, transform.m02() * x + transform.m12() * y + transform.m22() * z + transform.m32())
        }
        return result
    }

    private fun effectiveGeometryHash(bodyType: PhysicsBodyType3D, geometry: ShapeGeometry3D): Int
    {
        val triangleMeshFallback = geometry is TriangleMeshGeometry3D && bodyType != PhysicsBodyType3D.STATIC
        return 31 * geometry.hashCode() + triangleMeshFallback.hashCode()
    }

    private fun requireOpen()
    {
        check(!isClosed) { "Physics world is closed" }
    }

    private class ReusingAllocator(private val memory: MemorySegment) : SegmentAllocator
    {
        override fun allocate(byteSize: Long, byteAlignment: Long): MemorySegment
        {
            check(byteSize == memory.byteSize()) { "Native return value does not match reusable world scratch memory" }
            return memory
        }
    }

    private fun PhysicsBodyType3D.toNative() = when (this)
    {
        PhysicsBodyType3D.STATIC    -> b3_staticBody()
        PhysicsBodyType3D.KINEMATIC -> b3_kinematicBody()
        PhysicsBodyType3D.DYNAMIC   -> b3_dynamicBody()
    }

    companion object
    {
        private val ZERO = Vector3f()
        private val UNIT_SCALE = Vector3f(1f)
        private val IDENTITY_ROTATION = Quaternionf()
        private const val MIN_FALLBACK_BOX_HALF_SIZE = 0.005f

        fun MemorySegment.setVec3(value: Vector3fc): MemorySegment
        {
            b3Vec3.x(this, value.x())
            b3Vec3.y(this, value.y())
            b3Vec3.z(this, value.z())
            return this
        }

        fun MemorySegment.setQuaternion(value: Quaternionfc): MemorySegment
        {
            val vector = b3Quat.v(this)
            b3Vec3.x(vector, value.x())
            b3Vec3.y(vector, value.y())
            b3Vec3.z(vector, value.z())
            b3Quat.s(this,   value.w())
            return this
        }
    }
}