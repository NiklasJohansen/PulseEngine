package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.ComputeShader
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.CullingBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.BASE_INSTANCE
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.UNIFORM_OFFSET
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL43.GL_COMMAND_BARRIER_BIT
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT
import org.lwjgl.opengl.GL43.glDispatchCompute
import org.lwjgl.opengl.GL43.glMemoryBarrier

class WorldRenderDrawBuffer(
    val instanceBuffer: InstanceBufferObject
) {
    var gpuCullingSupported = false; private set
    
    private lateinit var program: ShaderProgram
    private lateinit var cullItemBatchIndexBuffer: StreamingIntBufferObject
    private lateinit var visibleIndexBuffer: StreamingIntBufferObject
    private lateinit var gpuCommandBuffer: StreamingIntBufferObject
    private lateinit var cpuCommandBuffer: StreamingIntBufferObject

    private val pendingGpuCullDispatches = ArrayList<GpuCullDispatch>(8)
    private var currentGpuCullPass = null as GpuCullPass?
    private var visibleInstanceCapacity = 0
    private var cullItemCount = 0
    private var initialized = false
    
    private var cpuCommandsSubmitted = false
    private var gpuCommandsSubmitted = false
    private var gpuCullMapSubmitted = false
    private var visibleIndicesSubmitted = false

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

    fun beginFrame(cullingBuffer: CullingBufferObject)
    {
        cullItemCount = cullingBuffer.size
        visibleInstanceCapacity = 0
        currentGpuCullPass = null
        pendingGpuCullDispatches.clear()
        cpuCommandsSubmitted = false
        gpuCommandsSubmitted = false
        gpuCullMapSubmitted = false
        visibleIndicesSubmitted = false

        if (this::cpuCommandBuffer.isInitialized)
            cpuCommandBuffer.clear()

        if (this::gpuCommandBuffer.isInitialized)
            gpuCommandBuffer.clear()

        if (this::cullItemBatchIndexBuffer.isInitialized)
            cullItemBatchIndexBuffer.clear()
    }

    fun beginCullPass()
    {
        if (!gpuCullingSupported) return

        require(currentGpuCullPass == null) { "A GPU culling pass is already being built" }

        val cullItemBatchIndexOffset = cullItemBatchIndexBuffer.size
        cullItemBatchIndexBuffer.fill(cullItemCount)
        {
            repeat(cullItemCount) { put(INVALID_BATCH_INDEX) }
        }

        currentGpuCullPass = GpuCullPass(cullItemBatchIndexOffset)
    }

    fun addCullCandidate(cullItemIndex: Int, commandIndex: Int)
    {
        if (!gpuCullingSupported) return

        val pass = currentGpuCullPass ?: return
        if (cullItemIndex !in 0 until cullItemCount)
            return

        cullItemBatchIndexBuffer[pass.cullItemBatchIndexOffset + cullItemIndex] = commandIndex
        pass.candidateInstanceCount++
    }

    fun submitCullPass(buckets: List<WorldRenderBucket>, frustum: Frustum)
    {
        if (!gpuCullingSupported)
        {
            submitCpuDraw(buckets, instanceBuffer)
            return
        }

        submitGpuCulledDraw(
            buckets = buckets,
            frustumSet = GpuCullFrustumSet.Single(frustum),
            commandSetCount = 1,
            // TODO: Clean up label passing
            label = { commandCount, candidateCount -> "frustum culling (${candidateCount}i, ${commandCount}c)" },
            instanceBuffer = instanceBuffer
        )
    }

    fun submitCullPass(bucket: WorldRenderBucket, frustumPlaneSets: Array<Frustum.FrustumPlaneSet>)
    {
        if (!gpuCullingSupported)
        {
            submitCpuDraw(listOf(bucket), instanceBuffer)
            return
        }

        submitGpuCulledDraw(
            buckets = listOf(bucket),
            frustumSet = GpuCullFrustumSet.PlaneSets(frustumPlaneSets),
            commandSetCount = frustumPlaneSets.size,
            label = { commandCount, candidateCount -> "cascade frustum culling (${candidateCount}i, ${frustumPlaneSets.size}x${commandCount}c)" },
            instanceBuffer = instanceBuffer
        )
    }

    private fun submitCpuDraw(buckets: List<WorldRenderBucket>, instanceBuffer: InstanceBufferObject)
    {
        if (!supportsCpuIndirect(instanceBuffer))
        {
            val payload = WorldRenderDrawPayload.DirectDrawPayload(
                instanceIndexMode = instanceBuffer.instanceIndexMode,
                instanceIndexBuffer = instanceBuffer.instanceIndexBuffer
            )
            buckets.forEach { it.drawPayload = payload }
            return
        }

        val commandBaseIndex = cpuCommandBuffer.size / INDIRECT_COMMAND_INTS
        var commandIndex = 0

        buckets.forEach { bucket ->
            require(bucket.commandStartIndex == commandIndex)
            {
                "CPU command layout is not contiguous: expected $commandIndex, got ${bucket.commandStartIndex}"
            }

            bucket.forEachBatch { batch ->
                cpuCommandBuffer.fill(INDIRECT_COMMAND_INTS)
                {
                    put(batch.subMesh.indexCount) // Count
                    put(batch.instanceCount)      // Instance count
                    put(batch.subMesh.indexStart) // First index
                    put(0)                        // Base vertex
                    put(batch.instanceIndex)      // Base instance
                }
                commandIndex++
            }
        }

        val payload = WorldRenderDrawPayload.IndirectDrawPayload(
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

    fun finishPreparation(cullData: CullingBufferObject)
    {
        if (this::cpuCommandBuffer.isInitialized && cpuCommandBuffer.size > 0)
        {
            cpuCommandBuffer.submit()
            cpuCommandsSubmitted = true
        }

        if (pendingGpuCullDispatches.isEmpty())
            return

        gpuCommandBuffer.submit()
        cullItemBatchIndexBuffer.submit()
        visibleIndexBuffer.reserve(visibleInstanceCapacity)
        gpuCommandsSubmitted = true
        gpuCullMapSubmitted = true
        visibleIndicesSubmitted = true

        pendingGpuCullDispatches.forEach()
        {
            GpuProfiler.measure(it.label)
            {
                cull(cullData, it)
            }
        }
    }

    fun markSubmittedDataInUse()
    {
        GpuProfiler.measure("sync world render draw buffers")
        {
            if (cpuCommandsSubmitted)    cpuCommandBuffer.markSubmittedDataInUse()
            if (gpuCommandsSubmitted)    gpuCommandBuffer.markSubmittedDataInUse()
            if (gpuCullMapSubmitted)     cullItemBatchIndexBuffer.markSubmittedDataInUse()
            if (visibleIndicesSubmitted) visibleIndexBuffer.markSubmittedDataInUse()
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

    private fun submitGpuCulledDraw(
        buckets: List<WorldRenderBucket>,
        frustumSet: GpuCullFrustumSet,
        commandSetCount: Int,
        label: (commandCount: Int, candidateCount: Int) -> String,
        instanceBuffer: InstanceBufferObject
    ) {
        val pass = currentGpuCullPass ?: throw IllegalStateException("beginCullPass() must be called before submitting GPU culled draw data")
        val commandCount = buckets.sumOf { it.size }
        val commandBaseIndex = gpuCommandBuffer.size / INDIRECT_COMMAND_INTS
        val visibleBaseIndex = visibleInstanceCapacity

        appendGpuCommands(
            buckets = buckets,
            commandSetCount = commandSetCount,
            candidateInstanceCount = pass.candidateInstanceCount,
            visibleBaseIndex = visibleBaseIndex
        )

        visibleInstanceCapacity += pass.candidateInstanceCount * commandSetCount

        val payload = WorldRenderDrawPayload.IndirectDrawPayload(
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
            candidateInstanceCount = pass.candidateInstanceCount,
            cullItemBatchIndexOffset = pass.cullItemBatchIndexOffset,
            frustumSet = frustumSet,
            label = label(commandCount, pass.candidateInstanceCount)
        )

        currentGpuCullPass = null
    }

    private fun appendGpuCommands(buckets: List<WorldRenderBucket>, commandSetCount: Int, candidateInstanceCount: Int, visibleBaseIndex: Int) 
    {
        for (commandSetIndex in 0 until commandSetCount)
        {
            var visibleStart = visibleBaseIndex + commandSetIndex * candidateInstanceCount
            var commandIndex = 0

            buckets.forEachFast { bucket ->

                require(bucket.commandStartIndex == commandIndex)
                {
                    "GPU command layout is not contiguous: expected $commandIndex, got ${bucket.commandStartIndex}"
                }

                bucket.forEachBatch { batch ->
                    gpuCommandBuffer.fill(INDIRECT_COMMAND_INTS)
                    {
                        put(batch.subMesh.indexCount) // Count
                        put(0)                        // Instance count, written by compute culling
                        put(batch.subMesh.indexStart) // First index
                        put(0)                        // Base vertex
                        put(visibleStart)             // Base instance into visible index buffer
                    }
                    visibleStart += batch.instanceCount
                    commandIndex++
                }
            }
        }
    }

    private fun cull(cullingBuffer: CullingBufferObject, dispatch: GpuCullDispatch)
    {
        if (cullItemCount == 0 || dispatch.candidateInstanceCount == 0 || dispatch.commandCount == 0)
            return

        cullingBuffer.bindSubmittedRanges()
        visibleIndexBuffer.bindSubmittedRange()
        cullItemBatchIndexBuffer.bindSubmittedRange()
        gpuCommandBuffer.bindSubmittedRange()

        program.bind()
        program.setUniform("uInstanceCount", cullItemCount)
        program.setUniform("uBatchCount", dispatch.commandCount)
        program.setUniform("uFrustumCount", dispatch.frustumSet.count)
        program.setUniform("uCommandBaseIndex", dispatch.commandBaseIndex)
        program.setUniform("uCullItemBatchIndexOffset", dispatch.cullItemBatchIndexOffset)
        program.setDispatchFrustums(dispatch.frustumSet)

        glDispatchCompute((cullItemCount + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE, 1, 1)
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT or GL_SHADER_STORAGE_BARRIER_BIT)
    }

    private fun ShaderProgram.setDispatchFrustums(frustumSet: GpuCullFrustumSet)
    {
        when (frustumSet)
        {
            is GpuCullFrustumSet.Single -> setFrustumUniforms(frustumSet.frustum, index = 0)
            is GpuCullFrustumSet.PlaneSets -> repeat(frustumSet.planeSets.size) { setPlaneSetUniforms(frustumSet.planeSets[it], it) }
        }
    }

    private fun ShaderProgram.setFrustumUniforms(frustum: Frustum, index: Int)
    {
        val offset = index * MAX_PLANES_PER_FRUSTUM
        setUniform(frustumPlaneCountUniformNames[index], FRUSTUM_PLANE_COUNT)
        setPlaneUniform(frustumPlaneUniformNames[offset + 0], frustum.left)
        setPlaneUniform(frustumPlaneUniformNames[offset + 1], frustum.right)
        setPlaneUniform(frustumPlaneUniformNames[offset + 2], frustum.bottom)
        setPlaneUniform(frustumPlaneUniformNames[offset + 3], frustum.top)
        setPlaneUniform(frustumPlaneUniformNames[offset + 4], frustum.near)
        setPlaneUniform(frustumPlaneUniformNames[offset + 5], frustum.far)
    }

    private fun ShaderProgram.setPlaneSetUniforms(frustumPlaneSet: Frustum.FrustumPlaneSet, index: Int)
    {
        val offset = index * MAX_PLANES_PER_FRUSTUM
        setUniform(frustumPlaneCountUniformNames[index], frustumPlaneSet.planeCount)

        for (i in 0 until frustumPlaneSet.planeCount)
            setPlaneUniform(frustumPlaneUniformNames[offset + i], frustumPlaneSet.planes[i])
    }

    private fun ShaderProgram.setPlaneUniform(name: String, plane: Frustum.FrustumPlane)
    {
        setUniform(name, plane.a, plane.b, plane.c, plane.d)
    }

    private class GpuCullPass(
        val cullItemBatchIndexOffset: Int,
        var candidateInstanceCount: Int = 0
    )

    private data class GpuCullDispatch(
        val commandBaseIndex: Int,
        val commandCount: Int,
        val candidateInstanceCount: Int,
        val cullItemBatchIndexOffset: Int,
        val frustumSet: GpuCullFrustumSet,
        val label: String
    )

    private sealed interface GpuCullFrustumSet
    {
        val count: Int

        data class Single(val frustum: Frustum) : GpuCullFrustumSet
        {
            override val count = 1
        }

        data class PlaneSets(val planeSets: Array<Frustum.FrustumPlaneSet>) : GpuCullFrustumSet
        {
            override val count = planeSets.size
        }
    }

    companion object
    {
        const val VISIBLE_INSTANCE_BUFFER_BINDING = 4
        private const val CULL_ITEM_BATCH_INDEX_BUFFER_BINDING = 10
        private const val COMMAND_BUFFER_BINDING = 6
        private const val BUFFER_SEGMENTS = 6
        private const val INDIRECT_COMMAND_INTS = WorldRenderDrawPayload.INDIRECT_COMMAND_INTS
        private const val INVALID_BATCH_INDEX = -1
        private const val FRUSTUM_PLANE_COUNT = 6
        private const val MAX_FRUSTUMS = 4
        private const val MAX_PLANES_PER_FRUSTUM = 24
        private const val WORK_GROUP_SIZE = 64
        private val frustumPlaneUniformNames = Array(MAX_FRUSTUMS * MAX_PLANES_PER_FRUSTUM) { "uFrustumPlanes[$it]" }
        private val frustumPlaneCountUniformNames = Array(MAX_FRUSTUMS) { "uFrustumPlaneCounts[$it]" }
    }
}