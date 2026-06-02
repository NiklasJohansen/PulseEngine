package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.ComputeShader
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.RenderItemBatchList
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode
import no.njoh.pulseengine.core.graphics.util.getSupportedModelInstanceIndexMode
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL40.*
import org.lwjgl.opengl.GL43.*

class WorldRenderItemGpuCuller private constructor()
{
    private lateinit var program: ShaderProgram
    private lateinit var cullItemBatchIndexBuffer: StreamingIntBufferObject
    private lateinit var visibleIndexBuffer: StreamingIntBufferObject
    private lateinit var commandBuffer: StreamingIntBufferObject

    private var cullItemCount = 0
    private var candidateInstanceCount = 0
    private var commandCount = 0
    private var submittedCommandSetCount = 0

    fun init(engine: PulseEngineInternal)
    {
        if (this::program.isInitialized)
            return
        
        program = ShaderProgram.create(engine.asset.loadNow(ComputeShader("/pulseengine/shaders/renderers/model_cull.comp")))
        cullItemBatchIndexBuffer = StreamingIntBufferObject.createShaderStorageBuffer(CULL_ITEM_BATCH_INDEX_BUFFER_BINDING, 512, BUFFER_SEGMENTS)
        visibleIndexBuffer = StreamingIntBufferObject.createShaderStorageBuffer(VISIBLE_INSTANCE_BUFFER_BINDING, 512, BUFFER_SEGMENTS)
        commandBuffer = StreamingIntBufferObject.createShaderStorageBuffer(COMMAND_BUFFER_BINDING, INDIRECT_COMMAND_INTS * 128, BUFFER_SEGMENTS)
    }

    fun clear(cullData: WorldRenderItemCullingData)
    {
        cullItemCount = cullData.itemCount
        candidateInstanceCount = 0
        commandCount = 0
        submittedCommandSetCount = 0
        cullItemBatchIndexBuffer.fillValue(cullItemCount, INVALID_BATCH_INDEX)
        commandBuffer.clear()
    }

    fun submitAndCull(batchLists: List<RenderItemBatchList>, frustum: Frustum, cullData: WorldRenderItemCullingData)
    {
        commandCount = batchLists.sumOf { it.size }

        GpuProfiler.measure({ "frustum culling (" plus candidateInstanceCount plus "i, " plus commandCount plus "c)" })
        {
            submit(batchLists, commandSetCount = 1)
            cull(cullData, frustumCount = 1) { setFrustumUniforms(frustum, index = 0) }
        }
    }

    fun submitAndCullCascades(batches: RenderItemBatchList, frustumPaneSets: Array<Frustum.FrustumPlaneSet>, cullData: WorldRenderItemCullingData)
    {
        commandCount = batches.size

        GpuProfiler.measure({ "cascade frustum culling (" plus candidateInstanceCount plus "i, " plus frustumPaneSets.size plus "x" plus commandCount plus "c)" })
        {
            submit(listOf(batches), frustumPaneSets.size)
            cull(cullData, frustumPaneSets.size) { repeat(frustumPaneSets.size) { setPlaneSetUniforms(frustumPaneSets[it], it) } }
        }
    }

    fun addInstance(cullItemIndex: Int, batchIndex: Int)
    {
        if (cullItemIndex !in 0 until cullItemCount)
            return

        cullItemBatchIndexBuffer.set(cullItemIndex, batchIndex)
        candidateInstanceCount++
    }

    fun bindVisibleInstanceBuffer()
    {
        visibleIndexBuffer.bindSubmittedRange()
    }

    fun bindIndirectCommandBuffer()
    {
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffer.id)
    }

    fun markSubmittedDataInUse()
    {
        if (!this::program.isInitialized)
            return

        GpuProfiler.measure("sync gpu culling buffers")
        {
            cullItemBatchIndexBuffer.markSubmittedDataInUse()
            visibleIndexBuffer.markSubmittedDataInUse()
            commandBuffer.markSubmittedDataInUse()
        }
    }

    fun destroy()
    {
        if (this::program.isInitialized) program.destroy()
        if (this::cullItemBatchIndexBuffer.isInitialized) cullItemBatchIndexBuffer.destroy()
        if (this::visibleIndexBuffer.isInitialized) visibleIndexBuffer.destroy()
        if (this::commandBuffer.isInitialized) commandBuffer.destroy()
    }

    fun getSubmittedIndirectCommandByteOffset(commandSetIndex: Int = 0, commandStartIndex: Int = 0): Long
    {
        require(commandSetIndex in 0 until maxOf(submittedCommandSetCount, 1)) { "Command set index out of range: $commandSetIndex" }
        require(commandStartIndex in 0..commandCount) { "Command start index out of range: $commandStartIndex" }
        return commandBuffer.getSubmittedDataByteOffset() + (commandSetIndex.toLong() * commandCount + commandStartIndex) * INDIRECT_COMMAND_STRIDE_BYTES
    }

    fun getSubmittedIndirectCommandBufferId() = commandBuffer.id

    private fun submit(batchLists: List<RenderItemBatchList>, commandSetCount: Int)
    {
        submittedCommandSetCount = commandSetCount
        commandBuffer.clear()
        commandBuffer.fill(commandCount * commandSetCount * INDIRECT_COMMAND_INTS)
        {
            for (commandSetIndex in 0 until commandSetCount)
            {
                var visibleStart = commandSetIndex * candidateInstanceCount
                var commandIndex = 0
                for (batches in batchLists)
                {
                    require(batches.commandStartIndex == commandIndex) 
                    {
                        "Model batch command layout is not contiguous: expected $commandIndex, got ${batches.commandStartIndex}"
                    }

                    batches.forEach()
                    {
                        // Index count
                        // Instance count (written by the culling compute shader)
                        // First index
                        // Base vertex
                        // Base instance
                        put(it.subMesh.indexCount, 0, it.subMesh.indexStart, 0, visibleStart)
                        visibleStart += it.instanceCount
                        commandIndex++
                    }
                }
            }
        }

        cullItemBatchIndexBuffer.submit()
        commandBuffer.submit()
        visibleIndexBuffer.reserve(candidateInstanceCount * commandSetCount) // Reserve space for the compute shader to write visible instance indices
    }

    private inline fun cull(cullData: WorldRenderItemCullingData, frustumCount: Int, setFrustums: ShaderProgram.() -> Unit)
    {
        if (cullItemCount == 0 || candidateInstanceCount == 0 || commandCount == 0) return

        visibleIndexBuffer.bindSubmittedRange()
        cullData.bindSubmittedRanges()
        cullItemBatchIndexBuffer.bindSubmittedRange()
        commandBuffer.bindSubmittedRange()

        program.bind()
        program.setUniform("uInstanceCount", cullItemCount)
        program.setUniform("uBatchCount", commandCount)
        program.setUniform("uFrustumCount", frustumCount)
        program.setFrustums()

        glDispatchCompute((cullItemCount + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE, 1, 1)
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT or GL_SHADER_STORAGE_BARRIER_BIT)
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

    companion object
    {
        fun createIfSupported(): WorldRenderItemGpuCuller?
        {
            if (!GlCapabilities.multiDrawIndirect ||
                !GlCapabilities.persistentMappedBuffers ||
                getSupportedModelInstanceIndexMode() != ModelInstanceIndexMode.BASE_INSTANCE
            ) {
                Logger.warn { "GpuModelCuller not supported on this platform" }
                return null // Not supported 
            }

            return WorldRenderItemGpuCuller()
        }

        const val VISIBLE_INSTANCE_BUFFER_BINDING = 4
        private const val CULL_ITEM_BATCH_INDEX_BUFFER_BINDING = 10
        private const val COMMAND_BUFFER_BINDING = 6
        private const val BUFFER_SEGMENTS = 6
        private const val INDIRECT_COMMAND_INTS = 5
        private const val INDIRECT_COMMAND_STRIDE_BYTES = INDIRECT_COMMAND_INTS * Int.SIZE_BYTES
        private const val INVALID_BATCH_INDEX = -1
        private const val FRUSTUM_PLANE_COUNT = 6
        private const val MAX_FRUSTUMS = 4
        private const val MAX_PLANES_PER_FRUSTUM = 24
        private const val WORK_GROUP_SIZE = 64
        private val frustumPlaneUniformNames = Array(MAX_FRUSTUMS * MAX_PLANES_PER_FRUSTUM) { "uFrustumPlanes[$it]" }
        private val frustumPlaneCountUniformNames = Array(MAX_FRUSTUMS) { "uFrustumPlaneCounts[$it]" }
    }
}