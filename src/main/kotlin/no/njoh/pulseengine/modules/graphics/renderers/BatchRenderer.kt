package no.njoh.pulseengine.modules.graphics.renderers

import no.njoh.pulseengine.modules.graphics.Surface2D

/**
 * Used to batch up vertex data into a single draw call.
 * Handled by the [Graphics] implementation.
 */
interface BatchRenderer
{
    /** Called once when the renderer is added to the [Surface2D] */
    fun init()

    /** Called every frame */
    fun render(surface: Surface2D)

    /** Called once when the [Surface2D] is destroyed */
    fun cleanUp()
}