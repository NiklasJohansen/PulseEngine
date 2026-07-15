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
import no.njoh.pulseengine.modules.physics3d.box3d.joints.Box3DJoint
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DShape
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DWorld
import no.njoh.pulseengine.modules.physics3d.entities.bodies.PhysicsBodyEntity3D
import no.njoh.pulseengine.modules.physics3d.entities.joints.PhysicsJointEntity3D
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

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
    private val jointBindingsByEntityId = TLongObjectHashMap<JointBinding>()
    private val activeJointBindings     = ArrayList<JointBinding>()
    private val liveJointEntityIds      = TLongHashSet()
    private val tmpPosition        = Vector3f()
    private val tmpRotation        = Quaternionf()
    private val gravity            = Vector3f()
    private val jointPosition      = Vector3f()
    private val jointRotation      = Quaternionf()
    private val bodyAPosition      = Vector3f()
    private val bodyARotation      = Quaternionf()
    private val bodyBPosition      = Vector3f()
    private val bodyBRotation      = Quaternionf()
    private val localPositionA     = Vector3f()
    private val localRotationA     = Quaternionf()
    private val localPositionB     = Vector3f()
    private val localRotationB     = Quaternionf()

    override fun onStart(engine: PulseEngine)
    {
        val world = getOrCreateWorld()
        updateSceneEntities(engine, world)
        updateSceneJoints(engine, world)
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (engine.scene.state != RUNNING) return

        val world = getOrCreateWorld()
        updateSceneEntities(engine, world)
        updateSceneJoints(engine, world)

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
        var jointIndex = activeJointBindings.lastIndex
        while (jointIndex >= 0)
            removeJointBinding(engine, activeJointBindings[jointIndex--])

        activeBindings.clear()
        bindingsByEntityId.clear()
        liveEntityIds.clear()
        activeJointBindings.clear()
        jointBindingsByEntityId.clear()
        liveJointEntityIds.clear()
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
        engine.scene.forEachEntityOfType<PhysicsBodyEntity3D> { entity ->

            val sceneEntity = entity as SceneEntity
            if (sceneEntity.isSet(DEAD))
                return@forEachEntityOfType

            liveEntityIds.add(sceneEntity.id)
            var binding = bindingsByEntityId[sceneEntity.id]
            
            if (binding == null || binding.physicsPropertyHash != entity.getPhysicsPropertyHash(engine))
            {
                if (binding != null)
                    removeBinding(engine, binding)

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
                removeBinding(engine, binding)
            index--
        }
    }

    private fun updateSceneJoints(engine: PulseEngine, world: Box3DWorld)
    {
        liveJointEntityIds.clear()
        engine.scene.forEachEntityOfType<PhysicsJointEntity3D> { entity ->
            val sceneEntity = entity as SceneEntity
            if (sceneEntity.isSet(DEAD))
                return@forEachEntityOfType

            liveJointEntityIds.add(sceneEntity.id)
            val bodyABinding = bindingsByEntityId[entity.bodyAEntityId]
            val bodyBBinding = bindingsByEntityId[entity.bodyBEntityId]
            var binding = jointBindingsByEntityId[sceneEntity.id]

            if (bodyABinding == null || bodyBBinding == null || bodyABinding === bodyBBinding)
            {
                if (binding != null)
                    removeJointBinding(engine, binding)
                return@forEachEntityOfType
            }

            val propertyHash = entity.physicsPropertyHash()
            if (binding == null ||
                binding.propertyHash != propertyHash ||
                binding.bodyABinding !== bodyABinding ||
                binding.bodyBBinding !== bodyBBinding ||
                !binding.joint.isValid()
            ) {
                if (binding != null)
                    removeJointBinding(engine, binding)

                bodyABinding.body.getTransform(bodyAPosition, bodyARotation)
                bodyBBinding.body.getTransform(bodyBPosition, bodyBRotation)
                jointPosition.set(entity.xPos, entity.yPos, entity.zPos)
                jointRotation.rotationXYZ(entity.xRot.toRadians(), entity.yRot.toRadians(), entity.zRot.toRadians())

                worldToBodyFrame(bodyAPosition, bodyARotation, jointPosition, jointRotation, localPositionA, localRotationA)
                worldToBodyFrame(bodyBPosition, bodyBRotation, jointPosition, jointRotation, localPositionB, localRotationB)

                val joint = world.createJoint(
                    bodyA = bodyABinding.body,
                    bodyB = bodyBBinding.body,
                    definition = entity.createPhysicsJointDefinition(localPositionA, localRotationA, localPositionB, localRotationB)
                )
                entity.bindPhysicsJoint(joint)

                binding = JointBinding(sceneEntity.id, joint, propertyHash, bodyABinding, bodyBBinding)
                jointBindingsByEntityId.put(sceneEntity.id, binding)
                activeJointBindings += binding
            }
        }

        var index = activeJointBindings.lastIndex
        while (index >= 0)
        {
            val binding = activeJointBindings[index]
            if (binding.entityId !in liveJointEntityIds)
                removeJointBinding(engine, binding)
            index--
        }
    }

    private fun removeBinding(engine: PulseEngine, binding: BodyBinding)
    {
        removeJointBindingsForBody(engine, binding)
        world?.destroyBody(binding.body)
        bindingsByEntityId.remove(binding.entityId)
        activeBindings.remove(binding)
    }

    private fun removeJointBindingsForBody(engine: PulseEngine, bodyBinding: BodyBinding)
    {
        var index = activeJointBindings.lastIndex
        while (index >= 0)
        {
            val binding = activeJointBindings[index]
            if (binding.bodyABinding === bodyBinding || binding.bodyBBinding === bodyBinding)
                removeJointBinding(engine, binding)
            index--
        }
    }

    private fun removeJointBinding(engine: PulseEngine, binding: JointBinding)
    {
        getPhysicsJointEntity(engine, binding.entityId)?.unbindPhysicsJoint()
        if (!binding.joint.isDestroyed)
            world?.destroyJoint(binding.joint)
        jointBindingsByEntityId.remove(binding.entityId)
        activeJointBindings.remove(binding)
    }

    private fun worldToBodyFrame(
        bodyPosition: Vector3fc,
        bodyRotation: Quaternionfc,
        jointPosition: Vector3fc,
        jointRotation: Quaternionfc,
        dstPosition: Vector3f,
        dstRotation: Quaternionf
    ) {
        dstRotation.set(bodyRotation).conjugate().normalize()
        dstPosition.set(jointPosition).sub(bodyPosition)
        dstRotation.transform(dstPosition)
        dstRotation.mul(jointRotation).normalize()
    }

    private fun getPhysicsEntity(engine: PulseEngine, entityId: Long) = engine.scene.getEntityOfType<PhysicsBodyEntity3D>(entityId)

    private fun getPhysicsJointEntity(engine: PulseEngine, entityId: Long) = engine.scene.getEntityOfType<PhysicsJointEntity3D>(entityId)

    private class BodyBinding(
        val entityId: Long,
        val body: Box3DBody,
        val physicsPropertyHash: Int
    )

    private class JointBinding(
        val entityId: Long,
        val joint: Box3DJoint,
        val propertyHash: Int,
        val bodyABinding: BodyBinding,
        val bodyBBinding: BodyBinding
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
