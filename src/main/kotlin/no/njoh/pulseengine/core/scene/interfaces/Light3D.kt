package no.njoh.pulseengine.core.scene.interfaces

import no.njoh.pulseengine.modules.scene.systems.Scene3DLightSource
import org.joml.Vector3f

/** 
 * Finite-radius 3D light. 
 */
interface Light3D : Scene3DLightSource, Translatable3D
{
    var radius: Float
}

/** 
 * A light whose influence is emitted through a cone. 
 */
interface ConicalLight3D : Light3D
{
    var outerConeAngle: Float

    fun getDirection(out: Vector3f): Vector3f
}