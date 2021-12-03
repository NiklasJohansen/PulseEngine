package no.njoh.pulseengine.modules.scene.systems.lighting

import no.njoh.pulseengine.data.Color

/**
 * Behaves like a light and is handled by the [LightingSystem].
 */
interface LightSource
{
    /** World X-position */
    var x: Float

    /** World Y-position */
    var y: Float

    /** World Z-position */
    var z: Float

    /** Direction of the light beam */
    var rotation: Float

    /** Determines the spread of the light beam */
    var coneAngle: Float

    /** Max reach of the light */
    var radius: Float

    /** Size of the light source */
    var size: Float

    /** Type of light source */
    var type: LightType

    /** Color of the light source */
    var color: Color

    /** Intensity of the light source */
    var intensity: Float

    /** Type of shadows */
    var shadowType: ShadowType
}