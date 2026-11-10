package no.njoh.pulseengine.core.graphics.scene3d.submission

import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.sqrt

class RenderDecal
{
    val worldToLocal      = Matrix4f()
    val position          = Vector3f()
    var radius            = 0f
    var materialId        = 0
    var opacity           = 1f
    var colorBlend        = 1f
    var colorBlendMode    = DecalColorBlendMode.MIX
    var normalBlend       = 1f
    var normalBlendMode   = DecalNormalBlendMode.COMBINE
    var roughnessBlend    = 1f
    var metallicBlend     = 0f
    var heightBlend       = 1f
    var edgeFade          = 0.05f
    var fadeStartAngleCos = 0.5f
    var fadeStartDistance = 80f
    var fadeEndDistance   = 100f
    var targetLayerMask   = 1
    var sortOrder         = 0
    var sequence          = 0

    fun setTransform(transform: Matrix4f)
    {
        transform.invert(worldToLocal)
        transform.getTranslation(position)

        var maxDistanceSquared = 0f
        for (i in 0 until 8)
        {
            val x = if (i and 1 == 0) -0.5f else 0.5f
            val y = if (i and 2 == 0) -0.5f else 0.5f
            val z = if (i and 4 == 0) -0.5f else 0.5f
            val dx = transform.m00() * x + transform.m10() * y + transform.m20() * z
            val dy = transform.m01() * x + transform.m11() * y + transform.m21() * z
            val dz = transform.m02() * x + transform.m12() * y + transform.m22() * z
            maxDistanceSquared = maxOf(maxDistanceSquared, dx * dx + dy * dy + dz * dz)
        }

        radius = sqrt(maxDistanceSquared)
    }
}

enum class DecalColorBlendMode(val gpuValue: Float)
{
    MIX(0f),
    MULTIPLY(1f)
}

enum class DecalNormalBlendMode(val gpuValue: Float)
{
    COMBINE(0f),
    REPLACE(1f)
}
