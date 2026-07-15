package no.njoh.pulseengine.modules.physics3d

import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.hash.TLongHashSet
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneState.RUNNING
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.BodyType3D.*
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DEventSink
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DShape
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DWorld
import no.njoh.pulseengine.modules.physics3d.entities.PhysicsEntity3D
import org.joml.Quaternionf
import org.joml.Vector3f

/** 
 * Owns, advances, and synchronizes the Box3D world used by 3D physics entities. 
 */
@Name("Physics (3D)")
@Icon("SHAPES")
class PhysicsSystem3D : SceneSystem()
{
    @Prop("Simulation", i=0, min=1f, max=32f) 
    var subStepCount = 4

    @Prop("Gravity [*G]", i=1) 
    var xGravity = 0f; var yGravity = -10f; var zGravity = 0f

    private var world: Box3DWorld? = null
    private val bindingsByEntityId = TLongObjectHashMap<BodyBinding>()
    private val activeBindings     = ArrayList<BodyBinding>()
    private val liveEntityIds      = TLongHashSet()
    private val tmpPosition        = Vector3f()
    private val tmpRotation        = Quaternionf()
    private val gravity            = Vector3f()

    override fun onStart(engine: PulseEngine)
    {
        val world = getOrCreateWorld()
        updateSceneEntities(engine, world)
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (engine.scene.state != RUNNING) return

        val world = getOrCreateWorld()
        updateSceneEntities(engine, world)

        activeBindings.forEachFast { getPhysicsEntity(engine, it.entityId)?.onPhysicsFixedUpdate(engine, it.body) }

        world.step(engine.data.fixedDeltaTime, subStepCount)
        world.dispatchEvents(engine, EventSink)

        activeBindings.forEachFast { binding ->
            val entity = getPhysicsEntity(engine, binding.entityId)
            if (entity != null && entity.bodyType != STATIC)
            {
                binding.body.getTransform(tmpPosition, tmpRotation)
                entity.onPhysicsTransformUpdated(tmpPosition, tmpRotation)
            }
        }
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled && engine.scene.state == RUNNING) onStart(engine) else onDestroy(engine)
    }

    override fun onStop(engine: PulseEngine) = onDestroy(engine)

    override fun onDestroy(engine: PulseEngine)
    {
        activeBindings.clear()
        bindingsByEntityId.clear()
        liveEntityIds.clear()
        world?.close()
        world = null
        gravity.set(0f)
    }

    private fun getOrCreateWorld(): Box3DWorld
    {
        if (world == null)
        {
            world = Box3DWorld(gravity.set(xGravity, yGravity, zGravity))
        }
        else if (xGravity != gravity.x || yGravity != gravity.y || zGravity != gravity.z)
        {
            world!!.setGravity(gravity.set(xGravity, yGravity, zGravity))
        }
        return world!!
    }

    private fun updateSceneEntities(engine: PulseEngine, world: Box3DWorld)
    {
        liveEntityIds.clear()
        engine.scene.forEachEntityOfType<PhysicsEntity3D> { entity ->

            val sceneEntity = entity as SceneEntity
            if (sceneEntity.isSet(DEAD))
                return@forEachEntityOfType

            liveEntityIds.add(sceneEntity.id)
            var binding = bindingsByEntityId[sceneEntity.id]
            
            if (binding == null || binding.physicsPropertyHash != entity.getPhysicsPropertyHash(engine))
            {
                if (binding != null)
                    removeBinding(binding)

                val shapeDefinitions = entity.getShapeDefinitions(engine)
                if (shapeDefinitions.isEmpty())
                    return@forEachEntityOfType

                val bodyDefinition = entity.getBodyDefinition()
                val body = world.createBody(
                    entityId = sceneEntity.id,
                    definition = bodyDefinition,
                    shapesDefinitions = shapeDefinitions,
                    enableContactEvents = entity is PhysicsContactListener3D,
                    enableSensorEvents = entity is PhysicsSensorListener3D
                )
                entity.onPhysicsBodyCreated(engine, body)

                binding = BodyBinding(sceneEntity.id, body, entity.getPhysicsPropertyHash(engine))
                bindingsByEntityId.put(sceneEntity.id, binding)
                activeBindings += binding
            }

            if (entity.hasPendingTransformChange())
            {
                tmpPosition.set(entity.xPos, entity.yPos, entity.zPos)
                tmpRotation.rotationXYZ(entity.xRot.toRadians(), entity.yRot.toRadians(), entity.zRot.toRadians())

                when (entity.bodyType)
                {
                    KINEMATIC -> binding.body.setTargetTransform(tmpPosition, tmpRotation, engine.data.fixedDeltaTime)
                    STATIC, DYNAMIC -> binding.body.setTransform(tmpPosition, tmpRotation)
                }

                entity.onExternalTransformApplied(tmpPosition, tmpRotation)
            }
        }

        var index = activeBindings.lastIndex
        while (index >= 0)
        {
            val binding = activeBindings[index]
            if (binding.entityId !in liveEntityIds)
                removeBinding(binding)
            index--
        }
    }

    private fun removeBinding(binding: BodyBinding)
    {
        world?.destroyBody(binding.body)
        bindingsByEntityId.remove(binding.entityId)
        activeBindings.remove(binding)
    }

    private fun getPhysicsEntity(engine: PulseEngine, entityId: Long) = engine.scene.getEntityOfType<PhysicsEntity3D>(entityId)

    private class BodyBinding(
        val entityId: Long,
        val body: Box3DBody,
        val physicsPropertyHash: Int
    )

    private object EventSink : Box3DEventSink
    {
        private val tmpContactPoint  = Vector3f()
        private val tmpContactNormal = Vector3f()

        override fun onContactStarted(engine: PulseEngine, shapeA: Box3DShape, shapeB: Box3DShape)
        {
            val entityIdA = shapeA.body.entityId
            val entityIdB = shapeB.body.entityId

            engine.scene.getEntityOfType<PhysicsContactListener3D>(entityIdA)?.onContactStarted(engine, entityIdB)

            if (entityIdA != entityIdB)
                engine.scene.getEntityOfType<PhysicsContactListener3D>(entityIdB)?.onContactStarted(engine, entityIdA)
        }

        override fun onContactEnded(engine: PulseEngine, shapeA: Box3DShape, shapeB: Box3DShape)
        {
            val entityIdA = shapeA.body.entityId
            val entityIdB = shapeB.body.entityId

            engine.scene.getEntityOfType<PhysicsContactListener3D>(entityIdA)?.onContactEnded(engine, entityIdB)

            if (entityIdA != entityIdB)
                engine.scene.getEntityOfType<PhysicsContactListener3D>(entityIdB)?.onContactEnded(engine, entityIdA)
        }

        override fun onContactHit(engine: PulseEngine, shapeA: Box3DShape, shapeB: Box3DShape, point: Vector3f, normal: Vector3f, approachSpeed: Float)
        {
            val entityIdA = shapeA.body.entityId
            val entityIdB = shapeB.body.entityId
            val listenerA = engine.scene.getEntityOfType<PhysicsContactListener3D>(entityIdA)

            if (listenerA != null)
            {
                tmpContactPoint.set(point)
                tmpContactNormal.set(normal).negate()
                listenerA.onContactHit(engine, entityIdB, tmpContactPoint, tmpContactNormal, approachSpeed)
            }

            if (entityIdA != entityIdB)
            {
                val listenerB = engine.scene.getEntityOfType<PhysicsContactListener3D>(entityIdB)
                if (listenerB != null)
                {
                    tmpContactPoint.set(point)
                    tmpContactNormal.set(normal)
                    listenerB.onContactHit(engine, entityIdA, tmpContactPoint, tmpContactNormal, approachSpeed)
                }
            }
        }

        override fun onSensorEntered(engine: PulseEngine, sensorShape: Box3DShape, visitorShape: Box3DShape)
        {
            engine.scene.getEntityOfType<PhysicsSensorListener3D>(sensorShape.body.entityId)?.onSensorEntered(engine, visitorShape.body.entityId)
        }

        override fun onSensorExited(engine: PulseEngine, sensorShape: Box3DShape, visitorShape: Box3DShape)
        {
            engine.scene.getEntityOfType<PhysicsSensorListener3D>(sensorShape.body.entityId)?.onSensorExited(engine, visitorShape.body.entityId)
        }
    }
}