package no.njoh.pulseengine.core.graphics.gpu.buffer

import no.njoh.pulseengine.core.graphics.scene3d.probe.LightProbe
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import org.joml.Vector3f

class LightProbeBufferObject : ShaderStorageBufferObject
{
    var probeCount = 0; private set

    private lateinit var probeBuffer: StreamingFloatBufferObject
    private val capturePosition = Vector3f()

    fun init()
    {
        if (!this::probeBuffer.isInitialized)
            probeBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(PROBE_FLOATS * 16)
    }

    fun clear()
    {
        probeBuffer.clear()
        probeCount = 0
    }

    fun addProbe(probe: LightProbe, cubeIndex: Int, capturePos: Vector3f)
    {
        probeBuffer.fill(PROBE_FLOATS)
        {
            val centerPos  = probe.position
            val rotation   = probe.rotation

            put(capturePos.x, capturePos.y, capturePos.z, cubeIndex.toFloat())
            put(centerPos.x, centerPos.y, centerPos.z, probe.blendDistance)
            put(probe.influenceSize.x * 0.5f, probe.influenceSize.y * 0.5f, probe.influenceSize.z * 0.5f, probe.specularIntensity)
            put(probe.projectionSize.x * 0.5f, probe.projectionSize.y * 0.5f, probe.projectionSize.z * 0.5f, probe.diffuseIntensity)
            put(-rotation.x, -rotation.y, -rotation.z, rotation.w) // Unit quaternion: world to probe-local.
        }
        probeCount++
    }

    fun submit()
    {
        measure("Light probe buffer")
        {
            probeBuffer.markSubmittedDataInUse()
            probeBuffer.submit()
        }
    }

    fun destroy()
    {
        probeBuffer.destroy()
        probeCount = 0
    }

    override fun bindStorageBuffer(binding: Int) = probeBuffer.bindStorageBuffer(binding)

    companion object
    {
        private const val PROBE_FLOATS = 20
    }
}