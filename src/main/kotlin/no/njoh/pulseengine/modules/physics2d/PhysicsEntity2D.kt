package no.njoh.pulseengine.modules.physics2d

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface

/**
 * Base class for all 2D physics entities.
 */
interface PhysicsEntity2D
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
    fun render(engine: PulseEngine, surface: Surface)
}