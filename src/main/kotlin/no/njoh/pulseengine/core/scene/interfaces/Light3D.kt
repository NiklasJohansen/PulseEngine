package no.njoh.pulseengine.core.scene.interfaces

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.scene.systems.Scene3DLightSource

/** 
 * Properties shared by concrete 3D light scene entities. 
 */
interface Light3D : Scene3DLightSource, Named, Translatable3D
{
    var color: Color
    var intensity: Float
    var radius: Float
    var shadowEnabled: Boolean
    var shadowResolution: Int
    var shadowBias: Float
    var shadowImportance: Float
}