package no.njoh.pulseengine.modules.scene.systems.rendering

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D

/**
 * Marks a [SceneEntity] as a target for custom render passes performed by the [EntityRenderer].
 */
interface CustomRenderPassTarget
{
    fun renderCustomPass(engine: PulseEngine, surface: Surface2D)
}