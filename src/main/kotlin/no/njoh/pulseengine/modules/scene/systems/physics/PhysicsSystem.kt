package no.njoh.pulseengine.modules.scene.systems.physics

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.scene.Scene
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.util.forEachFast

class PhysicsSystem : SceneSystem
{
    var worldWidth = 100_000
    var worldHeight = 100_000
    var gravity = 0.6f

    override fun onStart(scene: Scene)
    {
        scene.entityCollections.forEachFast { entities ->
            if (entities.size > 0 && entities[0] is RigidBody)
                entities.forEachFast { (it as RigidBody).init() }
        }
    }

    override fun onFixedUpdate(scene: Scene, engine: PulseEngine)
    {
        val spatialGrid = scene.spatialGrid
        scene.entityCollections.forEachFast { entities ->
            if (entities.size > 0 && entities[0] is RigidBody)
            {
                entities.forEachFast {
                    val body = it as RigidBody
                    if (body.bodyType == BodyType.DYNAMIC && !body.shape.isSleeping)
                    {
                        body.updateTransform(gravity)

                        // First iteration
                        body.updateConstraints()
                        body.updateCollisions(engine, spatialGrid)

                        // Second iteration
                        body.updateConstraints()
                        body.updateCollisions(engine, spatialGrid)

                        body.updateCenterAndRotation()
                        body.updateSleepState()
                        body.updateWorldConstraint(worldWidth, worldHeight)
                    }
                }
            }
        }
    }

    override fun onUpdate(scene: Scene, engine: PulseEngine) { }
    override fun onRender(scene: Scene, engine: PulseEngine) { }
}