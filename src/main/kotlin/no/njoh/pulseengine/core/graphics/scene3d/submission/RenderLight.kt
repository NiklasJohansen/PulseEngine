package no.njoh.pulseengine.core.graphics.scene3d.submission

import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Vector3f

class RenderLight
{
    val position         = Vector3f()
    val direction        = Vector3f()
    val color            = Color()
    var radius           = 0f
    var outerConeAngle   = 180f
    var innerConeAngle   = 180f
    var shadowEnabled    = false
    var shadowResolution = 512
    var shadowNearPlane  = 0.05f
    var shadowBias       = 0.005f
    var shadowImportance = 1f
    var shadowId         = 0L
    var shadowFaceOffset = -1
    var shadowFaceCount  = 0

    val isSpotLight get() = outerConeAngle < 180f
}