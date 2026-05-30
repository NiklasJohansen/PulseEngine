package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.objects.ModelBoneBuffer
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.joml.Matrix4f

class WorldRenderState
{
    private val boneBuffer = ModelBoneBuffer()
    private val viewSlots = ArrayList<ViewSlot>(1)
    private val shadowView = WorldShadowRenderView()
    private val queuedFrames = ArrayList<WorldRenderFrame>(2)
    private var queuedFrameCount = IntArray(2)
    private var boneFrame = null as WorldRenderFrame?
    private var shadowFrame = null as WorldRenderFrame?
    private var boneFrameVersion = -1
    
    inline fun getRenderView(engine: PulseEngineInternal, camera: Camera, frame: WorldRenderFrame, onUse: (WorldRenderView) -> Unit)
    {
        val view = getView(engine, camera, frame)
        onUse(view)
        finish(frame)
    }

    inline fun getShadowRenderView(
        engine: PulseEngineInternal,
        frame: WorldRenderFrame,
        shadowCullingMatrix: Matrix4f,
        cascadeFrustumPlaneSets: Array<FrustumPlaneSet>,
        onUse: (WorldShadowRenderView) -> Unit
    ) {
        val view = getShadowView(engine, frame, shadowCullingMatrix, cascadeFrustumPlaneSets)
        onUse(view)
        finish(frame)
    }

    fun queue(frame: WorldRenderFrame) = synchronized(this)
    {
        val index = queuedFrames.indexOfFrame(frame)
        if (index >= 0)
        {
            queuedFrameCount[index]++
            return
        }

        ensureQueuedFrameCapacity(queuedFrames.size + 1)
        queuedFrames += frame
        queuedFrameCount[queuedFrames.lastIndex] = 1
    }

    @PublishedApi
    internal fun getView(engine: PulseEngineInternal, camera: Camera, frame: WorldRenderFrame): WorldRenderView
    {
        beginBoneFrame(frame)

        for (i in 0 until viewSlots.size)
        {
            val slot = viewSlots[i]
            if (slot.camera === camera)
                return slot.view.update(engine, camera, frame, boneBuffer)
        }

        val view = WorldRenderView()
        viewSlots += ViewSlot(camera, view)
        return view.update(engine, camera, frame, boneBuffer)
    }

    @PublishedApi
    internal fun getShadowView(
        engine: PulseEngineInternal,
        frame: WorldRenderFrame,
        shadowCullingMatrix: Matrix4f,
        cascadeFrustumPlaneSets: Array<FrustumPlaneSet>
    ): WorldShadowRenderView {
        beginBoneFrame(frame)
        shadowFrame = frame
        return shadowView.update(engine, frame, boneBuffer, shadowCullingMatrix, cascadeFrustumPlaneSets)
    }

    fun destroy()
    {
        viewSlots.forEachFast { it.view.destroy() }
        shadowView.destroy()
        viewSlots.clear()
        queuedFrames.clear()
        boneBuffer.destroy()
        boneFrame = null
        shadowFrame = null
        boneFrameVersion = -1
    }

    @PublishedApi
    internal fun finish(frame: WorldRenderFrame)
    {
        var shouldMarkGpuDataInUse = false
        synchronized(this)
        {
            val index = queuedFrames.indexOfFrame(frame)
            if (index < 0) return

            val queuedPasses = queuedFrameCount[index]
            if (queuedPasses <= 0) return

            val remainingPasses = queuedPasses - 1
            queuedFrameCount[index] = remainingPasses
            shouldMarkGpuDataInUse = remainingPasses == 0
        }

        if (shouldMarkGpuDataInUse)
            markGpuDataInUse(frame)
    }

    private fun markGpuDataInUse(frame: WorldRenderFrame)
    {
        viewSlots.forEachFast()
        {
            if (it.view.isBuiltFrom(frame)) it.view.markGpuDataInUse()
        }

        if (shadowFrame === frame)
            shadowView.markGpuDataInUse()

        if (boneFrame === frame)
            boneBuffer.markSubmittedDataInUse()
    }

    private fun beginBoneFrame(frame: WorldRenderFrame)
    {
        if (boneFrame === frame && boneFrameVersion == frame.version)
            return

        boneBuffer.clear()
        boneFrame = frame
        boneFrameVersion = frame.version
    }

    private fun ensureQueuedFrameCapacity(requiredCapacity: Int)
    {
        if (requiredCapacity <= queuedFrameCount.size)
            return

        queuedFrameCount = queuedFrameCount.copyOf(maxOf(requiredCapacity, queuedFrameCount.size * 2))
    }

    private fun ArrayList<WorldRenderFrame>.indexOfFrame(frame: WorldRenderFrame): Int
    {
        for (i in 0 until size)
        {
            if (this[i] === frame) return i
        }
        return -1
    }

    private data class ViewSlot(
        val camera: Camera,
        val view: WorldRenderView
    )
}