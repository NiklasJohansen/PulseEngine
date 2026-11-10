package no.njoh.pulseengine.core.graphics.gpu.buffer

import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderDecal
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure

class DecalBufferObject
{
    lateinit var buffer: StreamingFloatBufferObject; private set
    
    var size = 0; private set
    
    private var submitted = false

    fun init()
    {
        buffer = StreamingFloatBufferObject.createShaderStorageBuffer(DECAL_FLOATS * 64)
    }

    fun clear()
    {
        buffer.clear()
        size = 0
        submitted = false
    }

    fun addDecal(decal: RenderDecal)
    {
        buffer.fill(DECAL_FLOATS)
        {
            put(decal.worldToLocal)
            put(decal.position.x, decal.position.y, decal.position.z)
            put(decal.colorBlendMode.gpuValue, decal.colorBlend)
            put(decal.normalBlendMode.gpuValue, decal.normalBlend)
            put(decal.roughnessBlend, decal.metallicBlend, decal.heightBlend, decal.opacity)
            put(decal.edgeFade, decal.fadeStartAngleCos, decal.fadeStartDistance, decal.fadeEndDistance)
            put(decal.materialId.toFloat(), Float.fromBits(decal.targetLayerMask))
            put(0f, 0f, 0f) // Padding
        }
        size++
    }

    fun submit()
    {
        if (size == 0) return

        measure("Decal buffer")
        {
            buffer.submit()
            submitted = true
        }
    }

    fun markSubmittedDataInUse()
    {
        if (!submitted) return

        measure("Decal buffer")
        {
            buffer.markSubmittedDataInUse()
            submitted = false
        }
    }

    fun destroy()
    {
        if (!::buffer.isInitialized) return
        
        buffer.destroy()
        submitted = false
    }

    companion object
    {
        const val DECAL_FLOATS = 36
    }
}