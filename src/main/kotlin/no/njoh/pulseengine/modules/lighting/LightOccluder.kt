package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Physical
import no.njoh.pulseengine.core.shared.primitives.Shape

/**
 * Will cast shadows behind the shape and is handled by the [LightingSystem].
 */
interface LightOccluder : Physical
{
    override val shape: Shape

    @get:Prop("Lighting", desc = "Whether or not shadow casting is enabled for this entity")
    var castShadows: Boolean
}