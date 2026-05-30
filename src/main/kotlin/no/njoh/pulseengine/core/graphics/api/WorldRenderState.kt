package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.objects.ModelBoneBufferObject
import org.joml.Matrix4f
import java.util.IdentityHashMap

class WorldRenderState
{
    @PublishedApi internal val cameraView = WorldRenderCameraView()
    @PublishedApi internal val shadowView = WorldRenderShadowView()

    private val boneBuffer = ModelBoneBufferObject()
    private val gpuFrameResources = IdentityHashMap<WorldRenderFrame, GpuFrameResource>(2)
    private val frameUsages = IdentityHashMap<WorldRenderFrame, Int>(2)
    private var boneFrame = null as WorldRenderFrame?
    private var boneFrameVersion = -1

    @PublishedApi
    internal inline fun withCameraView(engine: PulseEngineInternal, camera: Camera, frame: WorldRenderFrame, block: (WorldRenderCameraView) -> Unit)
    {
        val gpuResource = getOrCreateGpuFrameResource(frame)
        val view = cameraView.update(engine, camera, frame, gpuResource)
        block(view)
        release(frame)
    }

    @PublishedApi
    internal inline fun withShadowView(
        engine: PulseEngineInternal, 
        frame: WorldRenderFrame, 
        shadowCullingMatrix: Matrix4f, 
        cascadeFrustumPlaneSets: Array<FrustumPlaneSet>, 
        block: (WorldRenderShadowView) -> Unit
    ) {
        val gpuResource = getOrCreateGpuFrameResource(frame)
        val view = shadowView.update(engine, frame, gpuResource, shadowCullingMatrix, cascadeFrustumPlaneSets)
        block(view)
        release(frame)
    }

    fun registerUse(frame: WorldRenderFrame) = synchronized(this)
    {
        frameUsages[frame] = (frameUsages[frame] ?: 1) + 1
    }

    fun release(frame: WorldRenderFrame): Unit = synchronized(this)
    {
        val remaining = (frameUsages[frame] ?: 0) - 1
        if (remaining > 0) 
        {
            frameUsages[frame] = remaining
            return // Still in use, do not release
        }

        // No remaining uses, mark data in use
        if (cameraView.frame === frame) cameraView.markGpuDataInUse()
        if (shadowView.frame === frame) shadowView.markGpuDataInUse()
        if (boneFrame === frame) boneBuffer.markGpuDataInUse()

        gpuFrameResources[frame]?.markGpuDataInUse()
        frameUsages.remove(frame)
    }

    fun destroy()
    {
        gpuFrameResources.values.forEach { it.destroy() }
        cameraView.destroy()
        shadowView.destroy()
        gpuFrameResources.clear()
        boneBuffer.destroy()
        boneFrame = null
        boneFrameVersion = -1
    }

    fun getOrCreateGpuFrameResource(frame: WorldRenderFrame): GpuFrameResource
    {
        if (boneFrame !== frame || boneFrameVersion != frame.version)
        {
            boneBuffer.clear()
            boneFrame = frame
            boneFrameVersion = frame.version
        }

        gpuFrameResources[frame]?.let { return it.update(frame, boneBuffer) }

        val gpuResource = GpuFrameResource()
        gpuFrameResources[frame] = gpuResource
        return gpuResource.update(frame, boneBuffer)
    }
}