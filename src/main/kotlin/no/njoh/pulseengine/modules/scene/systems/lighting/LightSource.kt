package no.njoh.pulseengine.modules.scene.systems.lighting

import no.njoh.pulseengine.data.Color

/**
 * Behaves like a point light and is handled by the [LightingSystem].
 */
interface LightSource
{
    var x: Float
    var y: Float
    var radius: Float
    var color: Color
    var intensity: Float
}