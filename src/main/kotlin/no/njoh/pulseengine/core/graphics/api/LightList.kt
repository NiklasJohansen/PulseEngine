package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Vector3f

class LightList
{
    private val lights = MutableList<Light>(64) { Light() }
    private val culledLights = ArrayList<Light>(64)
    private var lightCount = 0

    fun reset() { lightCount = 0 }

    fun submitPointLight(x: Float, y: Float, z: Float, radius: Float, color: Color, intensity: Float)
    {
        ensurePointLightCapacity()
        val light = lights[lightCount++]
        light.position.set(x, y, z)
        light.direction.set(0f, 0f, 0f)
        light.radius = radius
        light.color.setFrom(color)
        light.intensity = intensity
        light.outerConeAngle = 180f
        light.innerConeAngle = 180f
    }

    fun submitSpotLight(
        xPos: Float,
        yPos: Float,
        zPos: Float,
        xDir: Float,
        yDir: Float,
        zDir: Float,
        radius: Float,
        color: Color,
        intensity: Float,
        innerConeAngle: Float,
        outerConeAngle: Float
    ) {
        ensurePointLightCapacity()
        val light = lights[lightCount++]
        light.position.set(xPos, yPos, zPos)
        light.direction.set(xDir, yDir, zDir)
        light.radius = radius
        light.color.setFrom(color)
        light.intensity = intensity
        light.outerConeAngle = outerConeAngle
        light.innerConeAngle = innerConeAngle
    }

    fun getFrustumCulledList(frustum: Frustum): ArrayList<Light>
    {
        culledLights.clear()
        for (i in 0 until lightCount)
        {
            val light = lights[i]
            val p = light.position
            if (frustum.intersectsSphere(p.x, p.y, p.z, light.radius))
                culledLights += light
        }
        return culledLights
    }

    private fun ensurePointLightCapacity()
    {
        if (lightCount >= lights.size) repeat(10) { lights.add(Light()) }
    }

    data class Light(
        val position: Vector3f = Vector3f(),
        val direction: Vector3f = Vector3f(),
        var radius: Float = 5f,
        val color: Color = Color(1f, 1f, 1f),
        var intensity: Float = 50f,
        var outerConeAngle: Float = 180f,
        var innerConeAngle: Float = 180f,
    )
}