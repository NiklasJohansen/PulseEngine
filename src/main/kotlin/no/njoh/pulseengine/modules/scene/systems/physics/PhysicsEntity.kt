package no.njoh.pulseengine.modules.scene.systems.physics

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D

/**
 * Base class for all physics entities.
 */
interface PhysicsEntity
{
    /**
     * Called once when the physics system starts.
     */
    fun init(engine: PulseEngine)

    /**
     * Called by the physics system once every fixed time step.
     * Suitable for updating velocities, accelerations and positions.
     */
    fun beginStep(engine: PulseEngine, timeStep: Float, gravity: Float)

    /**
     * Called N times by the physics system every fixed time step.
     * Suitable for iteratively solving collisions and constraints.
     * @param iteration - the current iteration
     * @param totalIterations - the total number of iterations to be performed
     */
    fun iterateStep(engine: PulseEngine, iteration: Int, totalIterations: Int, worldWidth: Int, worldHeight: Int)

    /**
     * Called every frame if enabled by the physics system.
     */
    fun render(engine: PulseEngine, surface: Surface2D)
}