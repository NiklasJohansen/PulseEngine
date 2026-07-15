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
import no.njoh.box3d.raw.Box3DRaw.b3DestroyJoint
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
import no.njoh.pulseengine.modules.physics3d.BodyType3D
import no.njoh.pulseengine.modules.physics3d.BoxGeometry3D
import no.njoh.pulseengine.modules.physics3d.CapsuleGeometry3D
import no.njoh.pulseengine.modules.physics3d.ConvexHullGeometry3D
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
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
        validate(definition, shapesDefinitions)

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
            b3BodyDef.isBullet(bodyDefinition, definition.bullet)
            
            val motionLocks = b3BodyDef.motionLocks(bodyDefinition)
            b3MotionLocks.angularX(motionLocks, definition.fixedRotation)
            b3MotionLocks.angularY(motionLocks, definition.fixedRotation)
            b3MotionLocks.angularZ(motionLocks, definition.fixedRotation)

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

    private fun createRevoluteJoint(bodyA: Box3DBody, bodyB: Box3DBody, definition: Box3DRevoluteJointDefinition): Box3DRevoluteJoint
    {
        requireJointBodies(bodyA, bodyB)
        validate(definition)

        Arena.ofConfined().use { tempArena ->
            val nativeDefinition = b3DefaultRevoluteJointDef(tempArena)

            setJointBase(
                b3RevoluteJointDef.base(nativeDefinition), bodyA, bodyB,
                definition.localPositionA, definition.localRotationA,
                definition.localPositionB, definition.localRotationB,
                definition.collideConnected
            )

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
            return Box3DRevoluteJoint(bodyA, bodyB, nativeJointId).also { joints += it }
        }
    }

    private fun createWeldJoint(bodyA: Box3DBody, bodyB: Box3DBody, definition: Box3DWeldJointDefinition): Box3DWeldJoint
    {
        requireJointBodies(bodyA, bodyB)
        validate(definition)

        Arena.ofConfined().use { tempArena ->
            val nativeDefinition = b3DefaultWeldJointDef(tempArena)

            setJointBase(
                b3WeldJointDef.base(nativeDefinition), bodyA, bodyB,
                definition.localPositionA, definition.localRotationA,
                definition.localPositionB, definition.localRotationB,
                definition.collideConnected
            )

            b3WeldJointDef.linearHertz(nativeDefinition, definition.linearHertz)
            b3WeldJointDef.linearDampingRatio(nativeDefinition, definition.linearDampingRatio)
            b3WeldJointDef.angularHertz(nativeDefinition, definition.angularHertz)
            b3WeldJointDef.angularDampingRatio(nativeDefinition, definition.angularDampingRatio)

            val nativeJointId = b3CreateWeldJoint(arena, nativeWorldId, nativeDefinition)
            return Box3DWeldJoint(bodyA, bodyB, nativeJointId).also { joints += it }
        }
    }

    private fun createDistanceJoint(bodyA: Box3DBody, bodyB: Box3DBody, definition: Box3DDistanceJointDefinition): Box3DDistanceJoint
    {
        requireJointBodies(bodyA, bodyB)
        validate(definition)

        Arena.ofConfined().use { tempArena ->
            val nativeDefinition = b3DefaultDistanceJointDef(tempArena)

            setJointBase(
                b3DistanceJointDef.base(nativeDefinition), bodyA, bodyB,
                definition.localPositionA, definition.localRotationA,
                definition.localPositionB, definition.localRotationB,
                definition.collideConnected
            )

            b3DistanceJointDef.length(nativeDefinition, definition.length)
            b3DistanceJointDef.enableSpring(nativeDefinition, definition.enableSpring)
            b3DistanceJointDef.hertz(nativeDefinition, definition.springHertz)
            b3DistanceJointDef.dampingRatio(nativeDefinition, definition.springDampingRatio)
            b3DistanceJointDef.enableLimit(nativeDefinition, definition.enableLimit)
            b3DistanceJointDef.minLength(nativeDefinition, definition.minLength)
            b3DistanceJointDef.maxLength(nativeDefinition, definition.maxLength)

            val nativeJointId = b3CreateDistanceJoint(arena, nativeWorldId, nativeDefinition)
            return Box3DDistanceJoint(bodyA, bodyB, nativeJointId).also { joints += it }
        }
    }

    private fun createWheelJoint(bodyA: Box3DBody, bodyB: Box3DBody, definition: Box3DWheelJointDefinition): Box3DWheelJoint
    {
        requireJointBodies(bodyA, bodyB)
        validate(definition)

        Arena.ofConfined().use { tempArena ->
            val nativeDefinition = b3DefaultWheelJointDef(tempArena)

            val baseDefinition = b3WheelJointDef.base(nativeDefinition)
            b3JointDef.bodyIdA(baseDefinition, bodyA.nativeBodyId)
            b3JointDef.bodyIdB(baseDefinition, bodyB.nativeBodyId)
            b3JointDef.collideConnected(baseDefinition, definition.collideConnected)

            val frameA = b3JointDef.localFrameA(baseDefinition)
            b3Transform.p(frameA).setVec3(definition.localPositionA)
            b3Transform.q(frameA).setQuaternion(definition.localRotationA)

            val frameB = b3JointDef.localFrameB(baseDefinition)
            b3Transform.p(frameB).setVec3(definition.localPositionB)
            b3Transform.q(frameB).setQuaternion(definition.localRotationB)

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
            return Box3DWheelJoint(bodyA, bodyB, nativeJointId).also { joints += it }
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

    private fun setJointBase(
        baseDefinition: MemorySegment,
        bodyA: Box3DBody,
        bodyB: Box3DBody,
        localPositionA: Vector3fc,
        localRotationA: Quaternionfc,
        localPositionB: Vector3fc,
        localRotationB: Quaternionfc,
        collideConnected: Boolean
    ) {
        b3JointDef.bodyIdA(baseDefinition, bodyA.nativeBodyId)
        b3JointDef.bodyIdB(baseDefinition, bodyB.nativeBodyId)
        b3JointDef.collideConnected(baseDefinition, collideConnected)

        val frameA = b3JointDef.localFrameA(baseDefinition)
        b3Transform.p(frameA).setVec3(localPositionA)
        b3Transform.q(frameA).setQuaternion(localRotationA)

        val frameB = b3JointDef.localFrameB(baseDefinition)
        b3Transform.p(frameB).setVec3(localPositionB)
        b3Transform.q(frameB).setQuaternion(localRotationB)
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

        val shapeId = when (val geometry = definition.geometry)
        {
            is BoxGeometry3D -> createBoxShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
            is SphereGeometry3D -> createSphereShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
            is CapsuleGeometry3D -> createCapsuleShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
            is ConvexHullGeometry3D -> createConvexHullShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
            is TriangleMeshGeometry3D -> createTriangleMeshShape(body.nativeBodyId, shapeDefinition, geometry, tmpArena)
        }

        val shape = Box3DShape(shapeId, body)
        shapesByNativeId.put(shapeKey(shapeId), shape)
        return shape
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

    private fun validate(body: Box3DBodyDefinition, shapes: List<Box3DShapeDefinition>)
    {
        for (shape in shapes)
        {
            require(body.type == BodyType3D.STATIC || shape.geometry !is TriangleMeshGeometry3D) 
            {
                "Triangle mesh collision is only supported on static bodies"
            }
        }
    }

    private fun validate(joint: Box3DWheelJointDefinition)
    {
        require(joint.suspensionHertz >= 0f) { "Suspension hertz must be non-negative" }
        require(joint.suspensionDampingRatio >= 0f) { "Suspension damping ratio must be non-negative" }
        require(joint.lowerSuspensionLimit <= joint.upperSuspensionLimit) { "Suspension limits are reversed" }
        require(joint.maxSpinTorque >= 0f) { "Maximum spin torque must be non-negative" }
        require(joint.steeringHertz >= 0f) { "Steering hertz must be non-negative" }
        require(joint.steeringDampingRatio >= 0f) { "Steering damping ratio must be non-negative" }
        require(joint.maxSteeringTorque >= 0f) { "Maximum steering torque must be non-negative" }
        require(joint.lowerSteeringLimit <= joint.upperSteeringLimit) { "Steering limits are reversed" }
    }

    private fun validate(joint: Box3DRevoluteJointDefinition)
    {
        val maxAngle = 0.99f * Math.PI.toFloat()
        require(joint.maxMotorTorque >= 0f) { "Maximum motor torque must be non-negative" }
        require(joint.springHertz >= 0f) { "Spring hertz must be non-negative" }
        require(joint.springDampingRatio >= 0f) { "Spring damping ratio must be non-negative" }
        require(joint.lowerAngle <= joint.upperAngle) { "Rotation limits are reversed" }
        require(joint.lowerAngle >= -maxAngle && joint.upperAngle <= maxAngle)
        {
            "Rotation limits must be within +/- 0.99 PI radians"
        }
    }

    private fun validate(joint: Box3DWeldJointDefinition)
    {
        require(joint.linearHertz >= 0f) { "Linear hertz must be non-negative" }
        require(joint.linearDampingRatio >= 0f) { "Linear damping ratio must be non-negative" }
        require(joint.angularHertz >= 0f) { "Angular hertz must be non-negative" }
        require(joint.angularDampingRatio >= 0f) { "Angular damping ratio must be non-negative" }
    }

    private fun validate(joint: Box3DDistanceJointDefinition)
    {
        require(joint.length > 0f) { "Distance must be positive" }
        require(joint.springHertz >= 0f) { "Spring hertz must be non-negative" }
        require(joint.springDampingRatio >= 0f) { "Spring damping ratio must be non-negative" }
        require(joint.minLength >= 0f) { "Minimum distance must be non-negative" }
        require(joint.minLength <= joint.maxLength) { "Distance limits are reversed" }
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

    private fun BodyType3D.toNative() = when (this)
    {
        BodyType3D.STATIC    -> b3_staticBody()
        BodyType3D.KINEMATIC -> b3_kinematicBody()
        BodyType3D.DYNAMIC   -> b3_dynamicBody()
    }

    companion object
    {
        private val ZERO = Vector3f()
        private val UNIT_SCALE = Vector3f(1f)
        private val IDENTITY_ROTATION = Quaternionf()

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