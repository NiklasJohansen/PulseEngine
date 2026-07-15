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
import no.njoh.box3d.raw.Box3DRaw.b3CreateSphereShape
import no.njoh.box3d.raw.Box3DRaw.b3CreateTransformedHullShape
import no.njoh.box3d.raw.Box3DRaw_1.b3CreateBody
import no.njoh.box3d.raw.Box3DRaw_1.b3CreateHull
import no.njoh.box3d.raw.Box3DRaw_1.b3CreateMesh
import no.njoh.box3d.raw.Box3DRaw_1.b3CreateWorld
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultBodyDef
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultShapeDef
import no.njoh.box3d.raw.Box3DRaw_1.b3DefaultWorldDef
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
import no.njoh.box3d.raw.b3Filter
import no.njoh.box3d.raw.b3MeshDef
import no.njoh.box3d.raw.b3MotionLocks
import no.njoh.box3d.raw.b3Quat
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
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.modules.physics3d.BodyType3D
import no.njoh.pulseengine.modules.physics3d.BoxGeometry3D
import no.njoh.pulseengine.modules.physics3d.CapsuleGeometry3D
import no.njoh.pulseengine.modules.physics3d.ConvexHullGeometry3D
import no.njoh.pulseengine.modules.physics3d.SphereGeometry3D
import no.njoh.pulseengine.modules.physics3d.TriangleMeshGeometry3D
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
        retainDestroyedShapes(body)
        b3DestroyBody(body.nativeBodyId)
        body.destroy()
        bodies.remove(body)
    }

    override fun close()
    {
        if (isClosed) return

        try
        {
            b3DestroyWorld(nativeWorldId)
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

    /** 
     * Applies the imported node transform while copying vertices into tmpArena native cooking memory. 
     */
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