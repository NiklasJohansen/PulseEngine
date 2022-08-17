package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.shared.annotations.ScnProp
import no.njoh.pulseengine.core.shared.primitives.Color

private const val GROUP = "Lighting"

/**
 * Defines the properties of a light source.
 * Handled by the [LightingSystem]
 */
interface LightSource
{
    @get:ScnProp(GROUP, 0, desc = "RGB-color of the light")
    var color: Color

    @get:ScnProp(GROUP, 1, min = 0f, desc = "Light intensity multiplier")
    var intensity: Float

    @get:ScnProp(GROUP, 2, min = 0f, desc = "Max light reach")
    var radius: Float

    @get:ScnProp(GROUP, 3, min = 0f, desc = "Size of the area emitting light")
    var size: Float

    @get:ScnProp(GROUP, 4, min = 0f, max = 360f, desc = "Determines the spread of the light beam in degrees")
    var coneAngle: Float

    @get:ScnProp(GROUP, 5, min = 0f, max = 1f, desc = "Determines how much light spills into shadow casters. Useful to illuminate walls etc.")
    var spill: Float

    @get:ScnProp(GROUP, 6, desc = "Type of light source")
    var type: LightType

    @get:ScnProp(GROUP, 7, desc = "The type of shadow the light will cast")
    var shadowType: ShadowType

    @get:ScnProp(GROUP, 8, desc = "X-position in world space")
    var x: Float

    @get:ScnProp(GROUP, 9, desc = "Y-position in world space")
    var y: Float

    @get:ScnProp(GROUP, 10, desc = "Z-position in world space")
    var z: Float

    @get:ScnProp(GROUP, 11, desc = "Direction of the light beam in degrees")
    var rotation: Float
}