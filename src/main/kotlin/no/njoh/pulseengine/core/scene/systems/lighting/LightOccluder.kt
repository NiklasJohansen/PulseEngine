package no.njoh.pulseengine.core.scene.systems.lighting

import no.njoh.pulseengine.core.shared.primitives.Shape

/**
 * Will cast shadows behind the shape and is handled by the [LightingSystem].
 */
interface LightOccluder
{
    val shape: Shape
    var castShadows: Boolean
}