package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.ComputeShader
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.CullingBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.world.DrawPayload.DirectDrawPayload
import no.njoh.pulseengine.core.graphics.api.world.DrawPayload.IndirectDrawPayload
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
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
    private lateinit var cullItemBatchIndexBuffer: StreamingIntBufferObject
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
            cullItemBatchIndexBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CULL_ITEM_BATCH_INDEX_BUFFER_BINDING, 512, BUFFER_SEGMENTS)
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

        if (this::cullItemBatchIndexBuffer.isInitialized)
            cullItemBatchIndexBuffer.clear()
    }

    inline fun prepareCullPass(frustumPlaneSets: Array<FrustumPlaneSet>, build: RenderPassBuilder.() -> Unit): PreparedRenderPass
    {
        val builder = createRenderPassBuilder(frustumPlaneSets)
        builder.build()
        return submitRenderPassBuilder(builder)
    }

    fun createRenderPassBuilder(frustumPlaneSets: Array<FrustumPlaneSet>): RenderPassBuilder
    {
        if (!gpuCullingSupported) 
            return RenderPassBuilder(frustumPlaneSets, null, 0)

        val cullItemBatchIndexOffset = cullItemBatchIndexBuffer.size
        val cullItemCount = cullingBuffer.size
        cullItemBatchIndexBuffer.fill(cullItemCount)
        {
            repeat(cullItemCount) { put(INVALID_BATCH_INDEX) }
        }

        return RenderPassBuilder(frustumPlaneSets, cullItemBatchIndexBuffer, cullItemBatchIndexOffset)
    }

    fun submitRenderPassBuilder(builder: RenderPassBuilder): PreparedRenderPass
    {
        val payload = if (gpuCullingSupported) submitGpuCulledDraw(builder) else submitCpuDraw(builder)
        val cullViewCount = if (builder.frustumPlaneSets.isEmpty()) 1 else builder.frustumPlaneSets.size

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

        gpuCommandBuffer.submit()
        cullItemBatchIndexBuffer.submit()
        visibleIndexBuffer.reserve(visibleInstanceCapacity)
        gpuBuffersSubmitted = true

        pendingGpuCullDispatches.forEach { cull(it) }
    }

    fun markSubmittedDataInUse()
    {
        GpuProfiler.measure("fence draw buffers")
        {
            if (cpuCommandsBufferSubmitted) 
                cpuCommandBuffer.markSubmittedDataInUse()

            if (gpuBuffersSubmitted)
            {
                gpuCommandBuffer.markSubmittedDataInUse()
                cullItemBatchIndexBuffer.markSubmittedDataInUse()
                visibleIndexBuffer.markSubmittedDataInUse()
            }
        }
    }

    fun destroy()
    {
        if (this::program.isInitialized) program.destroy()
        if (this::cpuCommandBuffer.isInitialized) cpuCommandBuffer.destroy()
        if (this::gpuCommandBuffer.isInitialized) gpuCommandBuffer.destroy()
        if (this::cullItemBatchIndexBuffer.isInitialized) cullItemBatchIndexBuffer.destroy()
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
        val cullViewCount    = builder.frustumPlaneSets.size
        val commandCount     = builder.batches.size
        val commandBaseIndex = gpuCommandBuffer.size / INDIRECT_COMMAND_INTS
        val visibleBaseIndex = visibleInstanceCapacity
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
            cullItemBatchIndexOffset = builder.gpuCullItemBatchIndexOffset,
            frustumPlaneSets = builder.frustumPlaneSets
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

        GpuProfiler.measure({ "frustum culling (" plus dispatch.cullInstanceCount plus "i, " plus dispatch.frustumPlaneSets.size plus "x" plus dispatch.commandCount plus "c)" })
        {
            cullingBuffer.bindSubmittedRanges()
            visibleIndexBuffer.bindSubmittedRange()
            cullItemBatchIndexBuffer.bindSubmittedRange()
            gpuCommandBuffer.bindSubmittedRange()

            program.bind()
            program.setUniform("uInstanceCount", cullingBuffer.size)
            program.setUniform("uBatchCount", dispatch.commandCount)
            program.setUniform("uFrustumCount", dispatch.frustumPlaneSets.size)
            program.setUniform("uCommandBaseIndex", dispatch.commandBaseIndex)
            program.setUniform("uCullItemBatchIndexOffset", dispatch.cullItemBatchIndexOffset)
            program.setFrustums(dispatch)

            glDispatchCompute((cullingBuffer.size + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE, 1, 1)
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT or GL_SHADER_STORAGE_BARRIER_BIT)
        }
    }

    private fun ShaderProgram.setFrustums(dispatch: GpuCullDispatch)
    {
        for (setIdx in 0 until dispatch.frustumPlaneSets.size)
        {
            val offset = setIdx * MAX_PLANES_PER_FRUSTUM
            val frustumPlaneSet = dispatch.frustumPlaneSets[setIdx]
            
            setUniform(frustumPlaneCountUniformNames[setIdx], frustumPlaneSet.size)

            for (planeIdx in 0 until frustumPlaneSet.size)
            {
                val plane = frustumPlaneSet[planeIdx]
                setUniform(frustumPlaneUniformNames[offset + planeIdx], plane.a, plane.b, plane.c, plane.d)
            }
        }
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
        val cullItemBatchIndexOffset: Int,
        val frustumPlaneSets: Array<FrustumPlaneSet>
    )

    companion object
    {
        const val VISIBLE_INSTANCE_BUFFER_BINDING = 4
        private const val CULL_ITEM_BATCH_INDEX_BUFFER_BINDING = 10
        private const val COMMAND_BUFFER_BINDING = 6
        private const val BUFFER_SEGMENTS = 6
        private const val INDIRECT_COMMAND_INTS = DrawPayload.INDIRECT_COMMAND_INTS
        private const val INVALID_BATCH_INDEX = -1
        private const val MAX_FRUSTUMS = 4
        private const val MAX_PLANES_PER_FRUSTUM = 24
        private const val WORK_GROUP_SIZE = 64
        private val frustumPlaneUniformNames = Array(MAX_FRUSTUMS * MAX_PLANES_PER_FRUSTUM) { "uFrustumPlanes[$it]" }
        private val frustumPlaneCountUniformNames = Array(MAX_FRUSTUMS) { "uFrustumPlaneCounts[$it]" }
    }
}