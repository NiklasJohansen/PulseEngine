package no.njoh.pulseengine.core.graphics.gpu.buffer

import no.njoh.pulseengine.core.graphics.scene3d.shadow.LocalShadowAtlas.ShadowFace
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderLight
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import kotlin.math.cos

class LightBufferObject
{
    lateinit var lightInstanceBuffer: StreamingFloatBufferObject private set
    lateinit var shadowFaceBuffer: StreamingFloatBufferObject    private set

    var lightCount = 0;      private set
    var shadowFaceCount = 0; private set

    private var submitted = false

    fun init()
    {
        if (this::lightInstanceBuffer.isInitialized)
            return

        lightInstanceBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(
            initCapacity = LIGHT_FLOATS * 128,
            segmentCount = BUFFER_SEGMENTS
        )
        shadowFaceBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(
            initCapacity = SHADOW_FACE_FLOATS * 64,
            segmentCount = BUFFER_SEGMENTS
        )
    }

    fun clear()
    {
        lightInstanceBuffer.clear()
        shadowFaceBuffer.clear()
        lightCount = 0
        shadowFaceCount = 0
    }

    fun addLight(light: RenderLight)
    {
        lightCount++
        lightInstanceBuffer.fill(LIGHT_FLOATS)
        {
            val color = light.color.asLinear()
            val isSpotLight = if (light.isSpotLight) 1f else 0f
            val castsShadow = if (light.shadowEnabled && light.shadowFaceOffset >= 0 && light.shadowFaceCount > 0) 1f else 0f
            val shadowBias = if (castsShadow > 0f) light.shadowBias else -1f

            put(light.position.x, light.position.y, light.position.z, light.range)
            put(color.red, color.green, color.blue, light.direction.x)
            put(light.direction.y, light.direction.z, cos(light.outerConeAngle.toRadians()), cos(light.innerConeAngle.toRadians()))
            put(isSpotLight, shadowBias, light.shadowFaceOffset.toFloat(), light.shadowFaceCount.toFloat())
            put(light.sourceRadius, 0f, 0f, 0f)
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

        measure("Local light buffers")
        {
            lightInstanceBuffer.submit()
            shadowFaceBuffer.submit()
            submitted = true
        }
    } 

    fun markSubmittedDataInUse()
    {
        if (!submitted) return

        measure("Local light buffers")
        {
            lightInstanceBuffer.markSubmittedDataInUse()
            shadowFaceBuffer.markSubmittedDataInUse()
            submitted = false
        }
    }

    fun destroy()
    {
        if (!this::lightInstanceBuffer.isInitialized)
            return
            
        lightInstanceBuffer.destroy()
        shadowFaceBuffer.destroy()
        submitted = false
    }

    companion object
    {
        private const val BUFFER_SEGMENTS = 3
        private const val LIGHT_FLOATS = 20
        private const val SHADOW_FACE_FLOATS = 20
    }
}