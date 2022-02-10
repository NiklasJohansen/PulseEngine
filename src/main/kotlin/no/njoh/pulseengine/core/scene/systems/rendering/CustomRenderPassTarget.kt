package no.njoh.pulseengine.core.scene.systems.rendering

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.Surface2D

/**
 * Marks a [SceneEntity] as a target for custom render passes performed by the [EntityRenderer].
 */
interface CustomRenderPassTarget
{
    fun renderCustomPass(engine: PulseEngine, surface: Surface2D)
}