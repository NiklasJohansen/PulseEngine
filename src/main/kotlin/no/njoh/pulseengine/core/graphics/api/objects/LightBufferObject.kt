package no.njoh.pulseengine.core.graphics.api.objects

import no.njoh.pulseengine.core.graphics.api.world.LocalShadowAtlas.ShadowFace
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderLight
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import kotlin.math.cos

class LightBufferObject
{
    private lateinit var lightBuffer: StreamingFloatBufferObject
    private lateinit var shadowFaceBuffer: StreamingFloatBufferObject

    var lightCount = 0;      private set
    var shadowFaceCount = 0; private set

    private var submitted = false

    fun init()
    {
        if (this::lightBuffer.isInitialized)
            return

        lightBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(
            blockBinding = LIGHT_BUFFER_BINDING,
            initCapacity = LIGHT_FLOATS * 128,
            segmentCount = BUFFER_SEGMENTS
        )
        shadowFaceBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(
            blockBinding = SHADOW_FACE_BUFFER_BINDING,
            initCapacity = SHADOW_FACE_FLOATS * 64,
            segmentCount = BUFFER_SEGMENTS
        )
    }

    fun bind()
    {
        lightBuffer.bindSubmittedRange()
        shadowFaceBuffer.bindSubmittedRange()
    }
    
    fun clear()
    {
        lightBuffer.clear()
        shadowFaceBuffer.clear()
        lightCount = 0
        shadowFaceCount = 0
    }

    fun addLight(light: WorldRenderLight)
    {
        lightCount++
        lightBuffer.fill(LIGHT_FLOATS)
        {
            val color = light.color.asLinear()
            val isSpotLight = if (light.isSpotLight) 1f else 0f
            val castsShadow = if (light.shadowEnabled && light.shadowFaceOffset >= 0 && light.shadowFaceCount > 0) 1f else 0f
            val shadowBias = if (castsShadow > 0f) light.shadowBias else -1f

            put(light.position.x, light.position.y, light.position.z, light.radius)
            put(color.red, color.green, color.blue, light.direction.x)
            put(light.direction.y, light.direction.z, cos(light.outerConeAngle.toRadians()), cos(light.innerConeAngle.toRadians()))
            put(isSpotLight, shadowBias, light.shadowFaceOffset.toFloat(), light.shadowFaceCount.toFloat())
        }
    }

    fun addShadowFace(shadowFace: ShadowFace)
    {
        shadowFaceCount++
        shadowFaceBuffer.fill(SHADOW_FACE_FLOATS)
        {
            val bias = shadowFace.atlasScaleBias
            put(bias.x, bias.y, bias.z, bias.w)
            put(shadowFace.viewProjection)
        }
    }

    fun submit()
    {
        if (lightCount == 0 && shadowFaceCount == 0) return

        measure("submit light buffer")
        {
            lightBuffer.submit()
            shadowFaceBuffer.submit()
            submitted = true
        }
    } 

    fun markSubmittedDataInUse()
    {
        if (!submitted) return

        lightBuffer.markSubmittedDataInUse()
        shadowFaceBuffer.markSubmittedDataInUse()
        submitted = false
    }

    fun destroy()
    {
        if (!this::lightBuffer.isInitialized)
            return
            
        lightBuffer.destroy()
        shadowFaceBuffer.destroy()
        submitted = false
    }

    companion object
    {
        const val LIGHT_BUFFER_BINDING = 13
        const val SHADOW_FACE_BUFFER_BINDING = 16

        private const val BUFFER_SEGMENTS = 3
        private const val LIGHT_FLOATS = 16
        private const val SHADOW_FACE_FLOATS = 20
    }
}