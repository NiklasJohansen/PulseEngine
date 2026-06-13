package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.ComputeShader
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.CullingBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingFloatBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.world.DrawPayload.DirectDrawPayload
import no.njoh.pulseengine.core.graphics.api.world.DrawPayload.IndirectDrawPayload
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.BASE_INSTANCE
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.UNIFORM_OFFSET
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL43.GL_COMMAND_BARRIER_BIT
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT
import org.lwjgl.opengl.GL43.glDispatchCompute
import org.lwjgl.opengl.GL43.glMemoryBarrier

class WorldRenderCommandBuilder(
    val instanceBuffer: InstanceBufferObject,
    val cullingBuffer: CullingBufferObject
) {
    var gpuCullingSupported = false; private set
    
    private lateinit var program: ShaderProgram
    private lateinit var cullItemIndexBuffer: StreamingIntBufferObject
    private lateinit var cullItemBatchIndexBuffer: StreamingIntBufferObject
    private lateinit var frustumMetadataBuffer: StreamingIntBufferObject
    private lateinit var frustumPlaneBuffer: StreamingFloatBufferObject
    private lateinit var visibleIndexBuffer: StreamingIntBufferObject
    private lateinit var gpuCommandBuffer: StreamingIntBufferObject
    private lateinit var cpuCommandBuffer: StreamingIntBufferObject

    private val pendingGpuCullDispatches = DynamicList<GpuCullDispatch>(8)
    private var visibleInstanceCapacity = 0

    private var initialized = false
    private var cpuCommandsBufferSubmitted = false
    private var gpuBuffersSubmitted = false

    fun init(engine: PulseEngineInternal)
    {
        if (initialized)
            return

        if (GlCapabilities.persistentMappedBuffers)
            cpuCommandBuffer = StreamingIntBufferObject.createDrawIndirectBuffer(INDIRECT_COMMAND_INTS * 256, BUFFER_SEGMENTS)

        gpuCullingSupported =
            GlCapabilities.multiDrawIndirect &&
            GlCapabilities.persistentMappedBuffers &&
            GlCapabilities.baseInstance &&
            getSupportedModelInstanceIndexMode() == BASE_INSTANCE

        if (gpuCullingSupported)
        {
            program = ShaderProgram.create(engine.asset.loadNow(ComputeShader("/pulseengine/shaders/renderers/model_cull.comp")))
            cullItemIndexBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CULL_ITEM_INDEX_BUFFER_BINDING, 512, BUFFER_SEGMENTS)
            cullItemBatchIndexBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CULL_ITEM_BATCH_INDEX_BUFFER_BINDING, 512, BUFFER_SEGMENTS)
            frustumMetadataBuffer = StreamingIntBufferObject.createShaderStorageBuffer(FRUSTUM_METADATA_BUFFER_BINDING, FRUSTUM_METADATA_INTS * 32, BUFFER_SEGMENTS)
            frustumPlaneBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(FRUSTUM_PLANE_BUFFER_BINDING, FRUSTUM_PLANE_FLOATS * 128, BUFFER_SEGMENTS)
            visibleIndexBuffer = StreamingIntBufferObject.createShaderStorageBuffer(VISIBLE_INSTANCE_BUFFER_BINDING, 512, BUFFER_SEGMENTS)
            gpuCommandBuffer = StreamingIntBufferObject.createShaderStorageBuffer(COMMAND_BUFFER_BINDING, INDIRECT_COMMAND_INTS * 128, BUFFER_SEGMENTS)
        }
        else Logger.warn { "GPU world item culling not supported on this platform" }

        initialized = true
    }

    fun beginFrame()
    {
        visibleInstanceCapacity = 0
        pendingGpuCullDispatches.clear()

        cpuCommandsBufferSubmitted = false
        gpuBuffersSubmitted = false

        if (this::cpuCommandBuffer.isInitialized)
            cpuCommandBuffer.clear()

        if (this::gpuCommandBuffer.isInitialized)
            gpuCommandBuffer.clear()

        if (this::cullItemIndexBuffer.isInitialized)
            cullItemIndexBuffer.clear()

        if (this::cullItemBatchIndexBuffer.isInitialized)
            cullItemBatchIndexBuffer.clear()

        if (this::frustumMetadataBuffer.isInitialized)
            frustumMetadataBuffer.clear()

        if (this::frustumPlaneBuffer.isInitialized)
            frustumPlaneBuffer.clear()
    }

    inline fun prepareCullPass(
        frustumPlaneSets: Array<FrustumPlaneSet>,
        frustumPlaneSetCount: Int = frustumPlaneSets.size,
        build: RenderPassBuilder.() -> Unit
    ): PreparedRenderPass
    {
        val builder = createRenderPassBuilder(frustumPlaneSets, frustumPlaneSetCount)
        builder.build()
        return submitRenderPassBuilder(builder)
    }

    fun createRenderPassBuilder(frustumPlaneSets: Array<FrustumPlaneSet>, frustumPlaneSetCount: Int = frustumPlaneSets.size): RenderPassBuilder
    {
        if (!gpuCullingSupported) 
            return RenderPassBuilder(frustumPlaneSets, null, 0, null, 0, frustumPlaneSetCount)

        val cullItemIndexOffset = cullItemIndexBuffer.size
        val cullItemBatchIndexOffset = cullItemBatchIndexBuffer.size

        return RenderPassBuilder(frustumPlaneSets, cullItemIndexBuffer, cullItemIndexOffset, cullItemBatchIndexBuffer, cullItemBatchIndexOffset, frustumPlaneSetCount)
    }

    fun submitRenderPassBuilder(builder: RenderPassBuilder): PreparedRenderPass
    {
        val payload = if (gpuCullingSupported) submitGpuCulledDraw(builder) else submitCpuDraw(builder)
        val cullViewCount = if (builder.frustumPlaneSetCount == 0) 1 else builder.frustumPlaneSetCount

        return PreparedRenderPass(payload, cullViewCount)
    }

    fun finishFramePreparation()
    {
        if (this::cpuCommandBuffer.isInitialized && cpuCommandBuffer.size > 0)
        {
            cpuCommandBuffer.submit()
            cpuCommandsBufferSubmitted = true
        }

        if (pendingGpuCullDispatches.isEmpty())
            return

        measure("command buffers submit")
        {
            gpuCommandBuffer.submit()
            cullItemIndexBuffer.submit()
            cullItemBatchIndexBuffer.submit()
            frustumMetadataBuffer.submit()
            frustumPlaneBuffer.submit()
            visibleIndexBuffer.reserve(visibleInstanceCapacity)
            gpuBuffersSubmitted = true
        }

        measure("frustum culling")
        {
            pendingGpuCullDispatches.forEach { cull(it) }
        }
    }

    fun markSubmittedDataInUse()
    {
        measure("fence draw buffers")
        {
            if (cpuCommandsBufferSubmitted) 
                cpuCommandBuffer.markSubmittedDataInUse()

            if (gpuBuffersSubmitted)
            {
                gpuCommandBuffer.markSubmittedDataInUse()
                cullItemIndexBuffer.markSubmittedDataInUse()
                cullItemBatchIndexBuffer.markSubmittedDataInUse()
                frustumMetadataBuffer.markSubmittedDataInUse()
                frustumPlaneBuffer.markSubmittedDataInUse()
                visibleIndexBuffer.markSubmittedDataInUse()
            }
        }
    }

    fun destroy()
    {
        if (this::program.isInitialized) program.destroy()
        if (this::cpuCommandBuffer.isInitialized) cpuCommandBuffer.destroy()
        if (this::gpuCommandBuffer.isInitialized) gpuCommandBuffer.destroy()
        if (this::cullItemIndexBuffer.isInitialized) cullItemIndexBuffer.destroy()
        if (this::cullItemBatchIndexBuffer.isInitialized) cullItemBatchIndexBuffer.destroy()
        if (this::frustumMetadataBuffer.isInitialized) frustumMetadataBuffer.destroy()
        if (this::frustumPlaneBuffer.isInitialized) frustumPlaneBuffer.destroy()
        if (this::visibleIndexBuffer.isInitialized) visibleIndexBuffer.destroy()
    }

    private fun submitCpuDraw(builder: RenderPassBuilder): DrawPayload
    {
        if (!supportsCpuIndirect(instanceBuffer))
            return DirectDrawPayload(instanceBuffer.instanceIndexMode, instanceBuffer.instanceIndexBuffer)

        val commandBaseIndex = cpuCommandBuffer.size / INDIRECT_COMMAND_INTS

        cpuCommandBuffer.fill(builder.batches.size * INDIRECT_COMMAND_INTS)
        {
            builder.batches.forEach()
            {
                putCommand(
                    indexCount    = it.mesh.indexCount,
                    instanceCount = it.instanceCount,
                    firstIndex    = it.mesh.indexStart,
                    vertexOffset  = 0,
                    baseInstance  = it.instanceIndex,
                )   
            }
        }

        return IndirectDrawPayload(
            commandBuffer = cpuCommandBuffer,
            commandBaseIndex = commandBaseIndex,
            cullViewCommandStride = 0,
            useVisibleInstanceBuffer = false,
            visibleInstanceBuffer = null,
            instanceIndexMode = instanceBuffer.instanceIndexMode,
            instanceIndexBuffer = instanceBuffer.instanceIndexBuffer
        )
    }

    private fun submitGpuCulledDraw(builder: RenderPassBuilder): DrawPayload
    {
        val cullViewCount    = builder.frustumPlaneSetCount
        val commandCount     = builder.batches.size
        val commandBaseIndex = gpuCommandBuffer.size / INDIRECT_COMMAND_INTS
        val visibleBaseIndex = visibleInstanceCapacity
        val frustumMetadataOffset = appendFrustumPlaneSets(builder.frustumPlaneSets, builder.frustumPlaneSetCount)
        var instanceCount    = 0
        
        gpuCommandBuffer.fill(cullViewCount * commandCount * INDIRECT_COMMAND_INTS)
        {
            repeat(cullViewCount)
            {
                builder.batches.forEach()
                {
                    putCommand(
                        indexCount    = it.mesh.indexCount,
                        instanceCount = 0, // Written by compute culling
                        firstIndex    = it.mesh.indexStart,
                        vertexOffset  = 0,
                        baseInstance  = visibleBaseIndex + instanceCount
                    )
                    instanceCount += it.instanceCount
                }
            }
        }

        visibleInstanceCapacity += instanceCount

        pendingGpuCullDispatches += GpuCullDispatch(
            commandBaseIndex = commandBaseIndex,
            commandCount = commandCount,
            cullInstanceCount = builder.gpuCullInstanceCount,
            cullItemIndexOffset = builder.gpuCullItemIndexOffset,
            cullItemBatchIndexOffset = builder.gpuCullItemBatchIndexOffset,
            frustumMetadataOffset = frustumMetadataOffset,
            frustumCount = cullViewCount
        )

        return IndirectDrawPayload(
            commandBuffer = gpuCommandBuffer,
            commandBaseIndex = commandBaseIndex,
            cullViewCommandStride = commandCount,
            useVisibleInstanceBuffer = true,
            visibleInstanceBuffer = visibleIndexBuffer,
            instanceIndexMode = instanceBuffer.instanceIndexMode,
            instanceIndexBuffer = instanceBuffer.instanceIndexBuffer
        )
    }

    private fun cull(dispatch: GpuCullDispatch)
    {
        if (cullingBuffer.size == 0 || dispatch.cullInstanceCount == 0 || dispatch.commandCount == 0)
            return

        measure({ "cull dispatch (" plus dispatch.cullInstanceCount plus "i, " plus dispatch.frustumCount plus "x" plus dispatch.commandCount plus "c)" })
        {
            cullingBuffer.bindSubmittedRanges()
            visibleIndexBuffer.bindSubmittedRange()
            cullItemIndexBuffer.bindSubmittedRange()
            cullItemBatchIndexBuffer.bindSubmittedRange()
            frustumMetadataBuffer.bindSubmittedRange()
            frustumPlaneBuffer.bindSubmittedRange()
            gpuCommandBuffer.bindSubmittedRange()

            program.bind()
            program.setUniform("uInstanceCount", dispatch.cullInstanceCount)
            program.setUniform("uBatchCount", dispatch.commandCount)
            program.setUniform("uFrustumCount", dispatch.frustumCount)
            program.setUniform("uCommandBaseIndex", dispatch.commandBaseIndex)
            program.setUniform("uCullItemIndexOffset", dispatch.cullItemIndexOffset)
            program.setUniform("uCullItemBatchIndexOffset", dispatch.cullItemBatchIndexOffset)
            program.setUniform("uFrustumMetadataOffset", dispatch.frustumMetadataOffset)

            glDispatchCompute((dispatch.cullInstanceCount + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE, 1, 1)
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT or GL_SHADER_STORAGE_BARRIER_BIT)
        }
    }

    private fun appendFrustumPlaneSets(frustumPlaneSets: Array<FrustumPlaneSet>, frustumPlaneSetCount: Int): Int
    {
        val metadataOffset = frustumMetadataBuffer.size / FRUSTUM_METADATA_INTS
        var planeOffset = frustumPlaneBuffer.size / FRUSTUM_PLANE_FLOATS
        var planeCount = 0

        for (i in 0 until frustumPlaneSetCount)
            planeCount += frustumPlaneSets[i].size

        frustumMetadataBuffer.fill(frustumPlaneSetCount * FRUSTUM_METADATA_INTS)
        {
            for (i in 0 until frustumPlaneSetCount)
            {
                val planeSet = frustumPlaneSets[i]
                put(planeOffset, planeSet.size)
                planeOffset += planeSet.size
            }
        }

        frustumPlaneBuffer.fill(planeCount * FRUSTUM_PLANE_FLOATS)
        {
            for (i in 0 until frustumPlaneSetCount)
            {
                val planeSet = frustumPlaneSets[i]
                for (planeIdx in 0 until planeSet.size)
                {
                    val plane = planeSet[planeIdx]
                    put(plane.a, plane.b, plane.c, plane.d)
                }
            }
        }

        return metadataOffset
    }

    private fun supportsCpuIndirect(instanceBuffer: InstanceBufferObject) =
        this::cpuCommandBuffer.isInitialized &&
        GlCapabilities.multiDrawIndirect &&
        GlCapabilities.persistentMappedBuffers &&
        instanceBuffer.instanceIndexMode != UNIFORM_OFFSET

    private data class GpuCullDispatch(
        val commandBaseIndex: Int,
        val commandCount: Int,
        val cullInstanceCount: Int,
        val cullItemIndexOffset: Int,
        val cullItemBatchIndexOffset: Int,
        val frustumMetadataOffset: Int,
        val frustumCount: Int
    )

    companion object
    {
        const val VISIBLE_INSTANCE_BUFFER_BINDING = 4
        private const val CULL_ITEM_INDEX_BUFFER_BINDING = 9
        private const val CULL_ITEM_BATCH_INDEX_BUFFER_BINDING = 10
        private const val FRUSTUM_METADATA_BUFFER_BINDING = 11
        private const val FRUSTUM_PLANE_BUFFER_BINDING = 2
        private const val COMMAND_BUFFER_BINDING = 6
        private const val BUFFER_SEGMENTS = 6
        private const val INDIRECT_COMMAND_INTS = DrawPayload.INDIRECT_COMMAND_INTS
        private const val FRUSTUM_METADATA_INTS = 2
        private const val FRUSTUM_PLANE_FLOATS = 4
        private const val WORK_GROUP_SIZE = 64
    }
}