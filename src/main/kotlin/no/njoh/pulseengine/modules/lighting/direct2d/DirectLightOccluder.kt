package no.njoh.pulseengine.modules.lighting.direct2d

import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Physical2D
import no.njoh.pulseengine.core.shared.primitives.Shape2D

/**
 * Will cast shadows behind the shape and is handled by the [DirectLightingSystem2D].
 */
interface DirectLightOccluder : Physical2D
{
    override val shape: Shape2D

    @get:Prop("Lighting", desc = "Whether or not shadow casting is enabled for this entity")
    var castShadows: Boolean
}