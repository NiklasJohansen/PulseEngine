package no.njoh.pulseengine.modules.lighting.direct

import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color

private const val GROUP = "Lighting"

/**
 * Defines the properties of a light source.
 * Handled by the [DirectLightingSystem]
 */
interface DirectLightSource
{
    @get:Prop(GROUP, 0, desc = "RGB-color of the light")
    var lightColor: Color

    @get:Prop(GROUP, 1, min = 0f, desc = "Light intensity multiplier")
    var intensity: Float

    @get:Prop(GROUP, 2, min = 0f, desc = "Max light reach")
    var radius: Float

    @get:Prop(GROUP, 3, min = 0f, desc = "Size of the area emitting light")
    var size: Float

    @get:Prop(GROUP, 4, min = 0f, max = 360f, desc = "Determines the spread of the light beam in degrees")
    var coneAngle: Float

    @get:Prop(GROUP, 5, min = 0f, max = 1f, desc = "Determines how much light spills into shadow casters. Useful to illuminate walls etc.")
    var spill: Float

    @get:Prop(GROUP, 6, desc = "Type of light source")
    var type: DirectLightType

    @get:Prop(GROUP, 7, desc = "The type of shadow the light will cast")
    var shadowType: DirectShadowType

    @get:Prop(GROUP, 8, desc = "X-position in world space")
    var x: Float

    @get:Prop(GROUP, 9, desc = "Y-position in world space")
    var y: Float

    @get:Prop(GROUP, 10, desc = "Z-position in world space")
    var z: Float

    @get:Prop(GROUP, 11, desc = "Direction of the light beam in degrees")
    var rotation: Float
}