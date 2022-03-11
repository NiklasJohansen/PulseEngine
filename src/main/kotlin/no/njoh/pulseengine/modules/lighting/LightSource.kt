package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.shared.primitives.Color

/**
 * Defines the properties of a light source.
 * Handled by the [LightingSystem]
 */
interface LightSource
{
    /** X-position in world space */
    var x: Float

    /** Y-position in world space */
    var y: Float

    /** Z-position in world space */
    var z: Float

    /** Direction of the light beam in degrees */
    var rotation: Float

    /** Determines the spread of the light beam in degrees (36o = full circle) */
    var coneAngle: Float

    /** Max light reach */
    var radius: Float

    /** Size of the area emitting light */
    var size: Float

    /** Type of light source */
    var type: LightType

    /** RGB-color of the light */
    var color: Color

    /** Light intensity multiplier */
    var intensity: Float

    /** Type of shadows */
    var shadowType: ShadowType

    /** Determines how much light spills into shadow casters. Useful to illuminate walls etc. */
    var spill: Float
}