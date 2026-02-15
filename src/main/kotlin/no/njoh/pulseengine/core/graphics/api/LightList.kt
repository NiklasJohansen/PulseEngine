package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Vector3f

class LightList
{
    private val pointLights = MutableList<PointLight>(64) { PointLight() }
    private val culledLights = ArrayList<PointLight>(64)
    private var pointLightCount = 0

    fun reset() { pointLightCount = 0 }

    fun submitPointLight(x: Float, y: Float, z: Float, radius: Float, color: Color, intensity: Float)
    {
        ensurePointLightCapacity()
        val light = pointLights[pointLightCount++]
        light.position.set(x, y, z)
        light.radius = radius
        light.color.setFrom(color)
        light.intensity = intensity
    }

    fun getFrustumCulledList(frustum: Frustum): ArrayList<PointLight>
    {
        culledLights.clear()
        for (i in 0 until pointLightCount)
        {
            val light = pointLights[i]
            val p = light.position
            if (frustum.intersectsSphere(p.x, p.y, p.z, light.radius))
                culledLights += light
        }
        return culledLights
    }

    private fun ensurePointLightCapacity()
    {
        if (pointLightCount >= pointLights.size) repeat(10) { pointLights.add(PointLight()) }
    }

    data class PointLight(
        val position: Vector3f = Vector3f(),
        var radius: Float = 5f,
        val color: Color = Color(1f, 1f, 1f),
        var intensity: Float = 50f
    )
}