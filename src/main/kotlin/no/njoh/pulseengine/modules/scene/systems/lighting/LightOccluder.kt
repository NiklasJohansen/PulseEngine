package no.njoh.pulseengine.modules.scene.systems.lighting

import no.njoh.pulseengine.modules.shared.primitives.Shape

/**
 * Will cast shadows behind the shape and is handled by the [LightingSystem].
 */
interface LightOccluder
{
    val shape: Shape
    var castShadows: Boolean
}