package no.njoh.pulseengine.modules.scene.systems.physics.bodies

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.SpatialGrid
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType
import no.njoh.pulseengine.modules.scene.systems.physics.CollisionResult

interface Body
{
    var bodyType: BodyType
    var friction: Float
    var elasticity: Float
    var drag: Float
    var mass: Float

    fun init()
    fun update(engine: PulseEngine, spatialGrid: SpatialGrid, gravity: Float, physicsIterations: Int, worldWidth: Int, worldHeight: Int)
    fun render(surface: Surface2D)

    fun onCollision(engine: PulseEngine, otherEntity: SceneEntity, result: CollisionResult)
}