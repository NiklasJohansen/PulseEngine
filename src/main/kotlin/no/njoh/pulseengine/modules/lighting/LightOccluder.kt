package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.shared.primitives.Physical
import no.njoh.pulseengine.core.shared.primitives.Shape

/**
 * Will cast shadows behind the shape and is handled by the [LightingSystem].
 */
interface LightOccluder : Physical
{
    override val shape: Shape
    var castShadows: Boolean
}