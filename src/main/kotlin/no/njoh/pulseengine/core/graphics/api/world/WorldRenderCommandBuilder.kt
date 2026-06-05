package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.ComputeShader
import no.njoh.pulseengine.core.graphics.api.Frustum.FrustumPlaneSet
import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.CullingBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderDrawPayload.DirectDrawPayload
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderDrawPayload.IndirectDrawPayload
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.BASE_INSTANCE
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.UNIFORM_OFFSET
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
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
    var currentCommandIndex = 0
    
    private lateinit var program: ShaderProgram
    private lateinit var cullItemBatchIndexBuffer: StreamingIntBufferObject
    private lateinit var visibleIndexBuffer: StreamingIntBufferObject
    private lateinit var gpuCommandBuffer: StreamingIntBufferObject
    private lateinit var cpuCommandBuffer: StreamingIntBufferObject

    private val pendingGpuCullDispatches = DynamicList<GpuCullDispatch>(8)
    private var currentGpuCullPass = null as GpuCullPass?
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
        currentGpuCullPass = null
        currentCommandIndex = 0
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

    fun beginCullPass()
    {
        if (gpuCullingSupported)
            require(currentGpuCullPass == null) { "A GPU culling pass is already being built" }

        currentCommandIndex = 0
        if (!gpuCullingSupported) return

        val cullItemBatchIndexOffset = cullItemBatchIndexBuffer.size
        val cullItemCount = cullingBuffer.size
        cullItemBatchIndexBuffer.fill(cullItemCount)
        {
            repeat(cullItemCount) { put(INVALID_BATCH_INDEX) }
        }

        currentGpuCullPass = GpuCullPass(cullItemBatchIndexOffset)
    }

    fun addGpuCullItem(cullItemIndex: Int, commandIndex: Int)
    {
        if (!gpuCullingSupported) return

        val pass = currentGpuCullPass ?: return
        if (cullItemIndex >= cullingBuffer.size)
            throw IllegalArgumentException("Cull item index out of bounds: $cullItemIndex")

        cullItemBatchIndexBuffer[pass.cullItemBatchIndexOffset + cullItemIndex] = commandIndex
        pass.cullInstanceCount++
    }

    fun submitCullPass(buckets: Array<WorldRenderBucket>, frustumPlaneSets: Array<FrustumPlaneSet>)
    {
        if (gpuCullingSupported)
            submitGpuCulledDraw(buckets, frustumPlaneSets)
        else 
            submitCpuDraw(buckets)
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

    private fun supportsCpuIndirect(instanceBuffer: InstanceBufferObject) =
        this::cpuCommandBuffer.isInitialized &&
        GlCapabilities.multiDrawIndirect &&
        GlCapabilities.persistentMappedBuffers &&
        instanceBuffer.instanceIndexMode != UNIFORM_OFFSET

    private fun submitCpuDraw(buckets: Array<WorldRenderBucket>)
    {
        if (!supportsCpuIndirect(instanceBuffer))
        {
            val payload = DirectDrawPayload(instanceBuffer.instanceIndexMode, instanceBuffer.instanceIndexBuffer)
            buckets.forEach { it.drawPayload = payload }
            return
        }

        val commandBaseIndex = cpuCommandBuffer.size / INDIRECT_COMMAND_INTS
        var commandIndex = 0

        buckets.forEach { bucket ->

            require(bucket.commandStartIndex == commandIndex) { "CPU command layout is not contiguous: expected $commandIndex, got ${bucket.commandStartIndex}" }

            bucket.forEachBatch { batch ->
                cpuCommandBuffer.fill(INDIRECT_COMMAND_INTS)
                {
                    put(batch.mesh.indexCount) // Count
                    put(batch.instanceCount)   // Instance count
                    put(batch.mesh.indexStart) // First index
                    put(0)                     // Base vertex
                    put(batch.instanceIndex)   // Base instance
                }
                commandIndex++
            }
        }

        val payload = IndirectDrawPayload(
            commandBuffer = cpuCommandBuffer,
            commandBaseIndex = commandBaseIndex,
            commandSetStride = 0,
            useVisibleInstanceBuffer = false,
            visibleInstanceBuffer = null,
            instanceIndexMode = instanceBuffer.instanceIndexMode,
            instanceIndexBuffer = instanceBuffer.instanceIndexBuffer
        )

        buckets.forEach { it.drawPayload = payload }
    }

    private fun submitGpuCulledDraw(buckets: Array<WorldRenderBucket>, frustumPlaneSets: Array<FrustumPlaneSet>) 
    {
        val pass = currentGpuCullPass ?: throw IllegalStateException("beginCullPass() must be called before submitting GPU culled draw data")
        val commandCount = buckets.sumOf { it.size }
        val commandBaseIndex = gpuCommandBuffer.size / INDIRECT_COMMAND_INTS
        val visibleBaseIndex = visibleInstanceCapacity

        appendGpuCommands(
            buckets = buckets,
            commandSetCount = frustumPlaneSets.size,
            cullInstanceCount = pass.cullInstanceCount,
            visibleBaseIndex = visibleBaseIndex
        )

        visibleInstanceCapacity += pass.cullInstanceCount * frustumPlaneSets.size

        val payload = IndirectDrawPayload(
            commandBuffer = gpuCommandBuffer,
            commandBaseIndex = commandBaseIndex,
            commandSetStride = commandCount,
            useVisibleInstanceBuffer = true,
            visibleInstanceBuffer = visibleIndexBuffer,
            instanceIndexMode = instanceBuffer.instanceIndexMode,
            instanceIndexBuffer = instanceBuffer.instanceIndexBuffer
        )
        buckets.forEach { it.drawPayload = payload }

        pendingGpuCullDispatches += GpuCullDispatch(
            commandBaseIndex = commandBaseIndex,
            commandCount = commandCount,
            cullInstanceCount = pass.cullInstanceCount,
            cullItemBatchIndexOffset = pass.cullItemBatchIndexOffset,
            frustumPlaneSets = frustumPlaneSets
        )

        currentGpuCullPass = null
    }

    private fun appendGpuCommands(buckets: Array<WorldRenderBucket>, commandSetCount: Int, cullInstanceCount: Int, visibleBaseIndex: Int) 
    {
        for (commandSetIndex in 0 until commandSetCount)
        {
            var visibleStart = visibleBaseIndex + commandSetIndex * cullInstanceCount
            var commandIndex = 0

            buckets.forEachFast { bucket ->

                require(bucket.commandStartIndex == commandIndex) { "GPU command layout is not contiguous: expected $commandIndex, got ${bucket.commandStartIndex}" }

                bucket.forEachBatch { batch ->
                    
                    gpuCommandBuffer.fill(INDIRECT_COMMAND_INTS)
                    {
                        put(batch.mesh.indexCount) // Count
                        put(0)                     // Instance count, written by compute culling
                        put(batch.mesh.indexStart) // First index
                        put(0)                     // Base vertex
                        put(visibleStart)          // Base instance into visible index buffer
                    }

                    visibleStart += batch.instanceCount
                    commandIndex++
                }
            }
        }
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

    private class GpuCullPass(
        val cullItemBatchIndexOffset: Int,
        var cullInstanceCount: Int = 0
    )

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
        private const val INDIRECT_COMMAND_INTS = WorldRenderDrawPayload.INDIRECT_COMMAND_INTS
        private const val INVALID_BATCH_INDEX = -1
        private const val MAX_FRUSTUMS = 4
        private const val MAX_PLANES_PER_FRUSTUM = 24
        private const val WORK_GROUP_SIZE = 64
        private val frustumPlaneUniformNames = Array(MAX_FRUSTUMS * MAX_PLANES_PER_FRUSTUM) { "uFrustumPlanes[$it]" }
        private val frustumPlaneCountUniformNames = Array(MAX_FRUSTUMS) { "uFrustumPlaneCounts[$it]" }
    }
}