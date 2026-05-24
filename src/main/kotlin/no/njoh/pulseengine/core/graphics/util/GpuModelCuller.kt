package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.ComputeShader
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.ModelBatchList
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.objects.StreamingFloatBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.BASE_INSTANCE
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER
import org.lwjgl.opengl.GL43.GL_COMMAND_BARRIER_BIT
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT
import org.lwjgl.opengl.GL43.glDispatchCompute
import org.lwjgl.opengl.GL43.glMemoryBarrier

class GpuModelCuller private constructor()
{
    private lateinit var program: ShaderProgram
    private lateinit var boundsBuffer: StreamingFloatBufferObject
    private lateinit var visibleIndexBuffer: StreamingIntBufferObject
    private lateinit var commandBuffer: StreamingIntBufferObject

    private var instanceCount = 0
    private var commandCount = 0

    fun init(engine: PulseEngineInternal)
    {
        if (this::program.isInitialized)
            return
        
        program = ShaderProgram.create(engine.asset.loadNow(ComputeShader("/pulseengine/shaders/renderers/model_cull.comp")))
        boundsBuffer = StreamingFloatBufferObject.createShaderStorageBuffer(BOUNDS_BUFFER_BINDING, INSTANCE_BOUNDS_FLOATS * 512)
        visibleIndexBuffer = StreamingIntBufferObject.createShaderStorageBuffer(VISIBLE_INSTANCE_BUFFER_BINDING, 512)
        commandBuffer = StreamingIntBufferObject.createShaderStorageBuffer(COMMAND_BUFFER_BINDING, INDIRECT_COMMAND_INTS * 128)
    }

    fun clear()
    {
        instanceCount = 0
        commandCount = 0
        boundsBuffer.clear()
        commandBuffer.clear()
    }

    fun submitAndCull(batches: ModelBatchList, frustum: Frustum)
    {
        commandCount = batches.size
        
        measure({"frustum culling (" plus instanceCount plus "i, " plus commandCount plus "c)"})
        {
            submit(batches)
            cull(frustum)
        }
    }

    fun addInstance(item: RenderItem, instanceIndex: Int, batchIndex: Int)
    {
        val bounds = item.cullingBounds

        boundsBuffer.fill(INSTANCE_BOUNDS_FLOATS)
        {
            put((bounds.xMin + bounds.xMax) * 0.5f) // X center
            put((bounds.yMin + bounds.yMax) * 0.5f) // Y center
            put((bounds.zMin + bounds.zMax) * 0.5f) // Z center
            put((bounds.xMax - bounds.xMin) * 0.5f) // X half
            put((bounds.yMax - bounds.yMin) * 0.5f) // Y half
            put((bounds.zMax - bounds.zMin) * 0.5f) // Z half
            put(batchIndex.toFloat())
            put(instanceIndex.toFloat())
        }
        instanceCount++
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

        boundsBuffer.markSubmittedDataInUse()
        visibleIndexBuffer.markSubmittedDataInUse()
        commandBuffer.markSubmittedDataInUse()
    }

    fun destroy()
    {
        if (this::program.isInitialized) program.destroy()
        if (this::boundsBuffer.isInitialized) boundsBuffer.destroy()
        if (this::visibleIndexBuffer.isInitialized) visibleIndexBuffer.destroy()
        if (this::commandBuffer.isInitialized) commandBuffer.destroy()
    }

    fun getSubmittedIndirectCommandByteOffset() = commandBuffer.getSubmittedDataByteOffset()

    private fun submit(batches: ModelBatchList)
    {
        commandBuffer.fill(batches.size * INDIRECT_COMMAND_INTS)
        {
            var visibleStart = 0
            batches.forEach()
            {
                // Index count
                // Instance count (written by the culling compute shader)
                // First index
                // Base vertex
                // Base instance
                put(it.subMesh.indexCount, 0, it.subMesh.indexStart, 0, visibleStart)
                visibleStart += it.instanceCount
            }
        }

        boundsBuffer.submit()
        commandBuffer.submit()
        visibleIndexBuffer.reserve(instanceCount) // Reserve space for the compute shader to write visible instance indices
    }

    private fun cull(frustum: Frustum)
    {
        if (instanceCount == 0 || commandCount == 0) return

        visibleIndexBuffer.bindSubmittedRange()
        boundsBuffer.bindSubmittedRange()
        commandBuffer.bindSubmittedRange()

        program.bind()
        program.setUniform("uInstanceCount", instanceCount)
        program.setPlaneUniform("uFrustumPlanes[0]", frustum.left)
        program.setPlaneUniform("uFrustumPlanes[1]", frustum.right)
        program.setPlaneUniform("uFrustumPlanes[2]", frustum.bottom)
        program.setPlaneUniform("uFrustumPlanes[3]", frustum.top)
        program.setPlaneUniform("uFrustumPlanes[4]", frustum.near)
        program.setPlaneUniform("uFrustumPlanes[5]", frustum.far)

        glDispatchCompute((instanceCount + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE, 1, 1)
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT or GL_SHADER_STORAGE_BARRIER_BIT)
    }

    private fun ShaderProgram.setPlaneUniform(name: String, plane: Frustum.FrustumPlane)
    {
        setUniform(name, plane.a, plane.b, plane.c, plane.d)
    }

    companion object
    {
        fun createIfSupported(): GpuModelCuller?
        {
            if (!GlCapabilities.multiDrawIndirect ||
                !GlCapabilities.persistentMappedBuffers ||
                getSupportedModelInstanceIndexMode() != BASE_INSTANCE
            ) {
                Logger.warn { "GpuModelCuller not supported on this platform" }
                return null // Not supported 
            }

            return GpuModelCuller()
        }

        const val VISIBLE_INSTANCE_BUFFER_BINDING = 4
        private const val BOUNDS_BUFFER_BINDING = 5
        private const val COMMAND_BUFFER_BINDING = 6
        private const val INSTANCE_BOUNDS_FLOATS = 8
        private const val INDIRECT_COMMAND_INTS = 5
        private const val WORK_GROUP_SIZE = 64
    }
}