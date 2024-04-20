package no.njoh.pulseengine.core.scene.interfaces

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface

/**
 * Gives the entity the ability to be rendered by the [EntityRenderer].
 */
interface Renderable
{
    /**
     * The Z-coordinate in world space used for depth sorting.
     */
    var z: Float

    fun onRender(engine: PulseEngine, surface: Surface)
}