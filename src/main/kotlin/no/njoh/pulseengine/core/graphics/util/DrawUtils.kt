package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.graphics.api.ModelBatch
import no.njoh.pulseengine.core.graphics.api.ModelBatchList
import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.captureIndirectDrawStats
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.incrementDrawStats
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.*
import org.lwjgl.opengl.GL11.GL_LINES
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL30.glVertexAttribIPointer
import org.lwjgl.opengl.GL31.glDrawArraysInstanced
import org.lwjgl.opengl.GL31.glDrawElementsInstanced
import org.lwjgl.opengl.GL32.glDrawElementsInstancedBaseVertex
import org.lwjgl.opengl.GL33.glVertexAttribDivisor
import org.lwjgl.opengl.GL42.glDrawArraysInstancedBaseInstance
import org.lwjgl.opengl.GL42.glDrawElementsInstancedBaseInstance
import org.lwjgl.opengl.GL42.glDrawElementsInstancedBaseVertexBaseInstance
import org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER
import org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect
import kotlin.math.max

object DrawUtils
{
    private var indirectCommandBuffer: StreamingIntBufferObject? = null

    fun drawTriangleIndices(vao: VertexArrayObject, firstIndex: Int, indexCount: Int)
    {
        vao.bind()
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, firstIndex.toLong() * Int.SIZE_BYTES)
        vao.release()
        incrementDrawStats(drawCommands = 1L, triangles = indexCount / 3L, instances = 1L)
    }

    fun drawTriangleVertices(vao: VertexArrayObject, firstVertexIndex: Int, vertexCount: Int)
    {
        vao.bind()
        glDrawArrays(GL_TRIANGLES, firstVertexIndex, vertexCount)
        vao.release()
        incrementDrawStats(drawCommands = 1L, triangles = vertexCount / 3L, instances = 1L)
    }

    fun drawTriangleStripVertices(vao: VertexArrayObject, firstVertex: Int, vertexCount: Int)
    {
        vao.bind()
        glDrawArrays(GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        vao.release()
        incrementDrawStats(drawCommands = 1L, triangles = max(0, vertexCount - 2L), instances = 1L)
    }

    fun drawInstancedTriangleStripVertices(vao: VertexArrayObject, firstVertex: Int, vertexCount: Int, instanceCount: Int)
    {
        vao.bind()
        glDrawArraysInstanced(GL_TRIANGLE_STRIP, firstVertex, vertexCount, instanceCount)
        vao.release()
        incrementDrawStats(
            drawCommands = 1L,
            triangles = instanceCount * max(0, vertexCount - 2L),
            instances = instanceCount.toLong()
        )
    }

    fun drawLineVertices(vao: VertexArrayObject, firstVertex: Int, vertexCount: Int)
    {
        vao.bind()
        glDrawArrays(GL_LINES, firstVertex, vertexCount)
        vao.release()
        incrementDrawStats(drawCommands = 1L, instances = 1L)
    }

    fun drawInstancedQuads(
        vao: VertexArrayObject,
        instanceBuffer: DoubleBufferedFloatObject,
        attributeLayout: VertexAttributeLayout,
        shaderProgram: ShaderProgram,
        firstInstanceIndex: Int,
        instanceCount: Int,
    ) {
        vao.bind()
        if (GlCapabilities.baseInstance)
        {
            glDrawArraysInstancedBaseInstance(GL_TRIANGLE_STRIP, 0, 4, instanceCount, firstInstanceIndex)
        }
        else // Fall back to glDrawArraysInstanced (macOS)
        {
            instanceBuffer.bind()
            attributeLayout.bind(shaderProgram, firstInstanceIndex)
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount)
        }
        vao.release()
        incrementDrawStats(drawCommands = 1L, triangles = instanceCount * 2L, instances = instanceCount.toLong())
    }

    fun drawModelBatches(
        batches: ModelBatchList,
        instanceIndexMode: ModelInstanceIndexMode,
        instanceIndexBuffer: StreamingIntBufferObject?
    ) {
        ModelBatch.resetBoundProgramAndCullMode()
        
        if (!GlCapabilities.multiDrawIndirect || !GlCapabilities.persistentMappedBuffers || instanceIndexMode == UNIFORM_OFFSET)
        {
            batches.forEach { it.drawDirect(instanceIndexMode, instanceIndexBuffer) }
            return
        }

        var totalCommandCount = 0
        val commandBuffer = getIndirectCommandBuffer()
        commandBuffer.clear()

        batches.forEach { batch ->

            if (batch.model.vao == null) return@forEach
 
            commandBuffer.fill(INDIRECT_COMMAND_INTS)
            {
                put(batch.subMesh.indexCount) // Count
                put(batch.instanceCount)      // Instance count
                put(batch.subMesh.indexStart) // First index
                put(0)                        // Base vertex
                put(batch.instanceIndex)      // Base instance
            }
            totalCommandCount++
        }

        if (totalCommandCount == 0)
            return

        commandBuffer.submit()

        var groupStart = null as ModelBatch?
        var groupVao = null as VertexArrayObject?
        var groupCommandStart = 0
        var commandIndex = 0
        var commandCount = 0
        var triangleCount = 0L
        var instanceCount = 0L

        fun flushGroup()
        {
            val firstBatch = groupStart ?: return
            val vao = groupVao ?: return

            firstBatch.bindProgramAndSetCullMode()
            firstBatch.program.setUniform("uUseVisibleInstanceBuffer", false)
            vao.bind()

            if (instanceIndexMode == INSTANCE_ATTRIBUTE)
                bindInstanceIndexAttribute(instanceIndexBuffer)

            commandBuffer.bind()
            glMultiDrawElementsIndirect(
                GL_TRIANGLES,
                GL_UNSIGNED_INT,
                commandBuffer.getSubmittedDataByteOffset() + groupCommandStart.toLong() * INDIRECT_COMMAND_STRIDE_BYTES,
                commandCount,
                0
            )
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0)

            if (instanceIndexMode == INSTANCE_ATTRIBUTE)
                instanceIndexBuffer?.release()

            vao.release()
            incrementDrawStats(drawCommands = commandCount.toLong(), triangles = triangleCount, instances = instanceCount)

            groupStart = null
            groupVao = null
            groupCommandStart = 0
            commandCount = 0
            triangleCount = 0L
            instanceCount = 0L
        }

        batches.forEach { batch ->

            val vao = batch.model.vao ?: return@forEach
            val firstBatch = groupStart

            if (firstBatch == null)
            {
                groupStart = batch
                groupVao = vao
                groupCommandStart = commandIndex
            }
            else if (firstBatch.program !== batch.program || firstBatch.cullMode != batch.cullMode || groupVao !== vao)
            {
                flushGroup()
                groupStart = batch
                groupVao = vao
                groupCommandStart = commandIndex
            }

            commandCount++
            triangleCount += batch.instanceCount * (batch.subMesh.indexCount / 3L)
            instanceCount += batch.instanceCount
            commandIndex++
        }

        flushGroup()
        commandBuffer.markSubmittedDataInUse()
    }

    fun drawGpuCulledModelBatches(batches: ModelBatchList, culler: GpuModelCuller, commandSetIndex: Int = 0)
    {
        if (batches.size == 0) return

        ModelBatch.resetBoundProgramAndCullMode()
        culler.bindVisibleInstanceBuffer()

        var groupStart = null as ModelBatch?
        var groupVao = null as VertexArrayObject?
        var groupCommandStart = 0
        var commandIndex = 0
        var commandCount = 0

        fun flushGroup()
        {
            val firstBatch = groupStart ?: return
            val vao = groupVao ?: return

            firstBatch.bindProgramAndSetCullMode()
            firstBatch.program.setUniform("uUseVisibleInstanceBuffer", true)
            vao.bind()
            culler.bindIndirectCommandBuffer()
            val commandByteOffset =
                culler.getSubmittedIndirectCommandByteOffset(commandSetIndex) +
                groupCommandStart.toLong() * INDIRECT_COMMAND_STRIDE_BYTES

            glMultiDrawElementsIndirect(
                /* mode = */ GL_TRIANGLES,
                /* type = */ GL_UNSIGNED_INT,
                /* indirect = */ commandByteOffset,
                /* drawcount = */ commandCount,
                /* stride = */ 0
            )

            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0)
            vao.release()
            captureIndirectDrawStats(culler.getSubmittedIndirectCommandBufferId(), commandByteOffset, commandCount)

            groupStart = null
            groupVao = null
            commandCount = 0
        }

        batches.forEach { batch ->
            val vao = batch.model.vao
            if (vao == null)
            {
                commandIndex++
                return@forEach
            }

            val firstBatch = groupStart

            if (firstBatch == null)
            {
                groupStart = batch
                groupVao = vao
                groupCommandStart = commandIndex
            }
            else if (firstBatch.program !== batch.program || firstBatch.cullMode != batch.cullMode || groupVao !== vao)
            {
                flushGroup()
                groupStart = batch
                groupVao = vao
                groupCommandStart = commandIndex
            }

            commandCount++
            commandIndex++
        }

        flushGroup()
    }

    private fun ModelBatch.drawDirect(instanceIndexMode: ModelInstanceIndexMode, instanceIndexBuffer: StreamingIntBufferObject?)
    {
        val vao = model.vao ?: return
        bindProgramAndSetCullMode()
        program.setUniform("uUseVisibleInstanceBuffer", false)
        drawInstancedTriangleIndices(
            program = program,
            vao = vao,
            instanceIndexMode = instanceIndexMode,
            instanceIndexBuffer = instanceIndexBuffer,
            firstIndex = subMesh.indexStart,
            indexCount = subMesh.indexCount,
            instanceIndex = instanceIndex,
            instanceCount = instanceCount,
            baseVertex = 0
        )
    }

    fun drawInstancedTriangleIndices(
        program: ShaderProgram,
        vao: VertexArrayObject,
        instanceIndexMode: ModelInstanceIndexMode,
        instanceIndexBuffer: StreamingIntBufferObject?,
        firstIndex: Int,
        indexCount: Int,
        instanceIndex: Int,
        instanceCount: Int,
        baseVertex: Int = 0
    ) {
        if (instanceCount == 0)
            return

        vao.bind()

        when (instanceIndexMode)
        {
            BASE_INSTANCE ->
            {
                if (baseVertex == 0)
                    glDrawElementsInstancedBaseInstance(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, firstIndex.toLong() * Int.SIZE_BYTES, instanceCount, instanceIndex)
                else
                    glDrawElementsInstancedBaseVertexBaseInstance(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, firstIndex.toLong() * Int.SIZE_BYTES, instanceCount, baseVertex, instanceIndex)
            }
            INSTANCE_ATTRIBUTE ->
            {
                bindInstanceIndexAttribute(instanceIndexBuffer)
                if (baseVertex == 0)
                    glDrawElementsInstancedBaseInstance(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, firstIndex.toLong() * Int.SIZE_BYTES, instanceCount, instanceIndex)
                else
                    glDrawElementsInstancedBaseVertexBaseInstance(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, firstIndex.toLong() * Int.SIZE_BYTES, instanceCount, baseVertex, instanceIndex)
                instanceIndexBuffer?.release()
            }
            UNIFORM_OFFSET ->
            {
                program.setUniform("uInstanceOffset", instanceIndex)
                if (baseVertex == 0)
                    glDrawElementsInstanced(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, firstIndex.toLong() * Int.SIZE_BYTES, instanceCount)
                else
                    glDrawElementsInstancedBaseVertex(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, firstIndex.toLong() * Int.SIZE_BYTES, instanceCount, baseVertex)
            }
        }

        vao.release()
        incrementDrawStats(drawCommands = 1L, triangles = instanceCount * (indexCount / 3L), instances = instanceCount.toLong())
    }

    private const val INSTANCE_INDEX_ATTRIBUTE_LOCATION = 6
    private const val INDIRECT_COMMAND_INTS = 5
    private const val INDIRECT_COMMAND_STRIDE_BYTES = 5 * Int.SIZE_BYTES

    private fun bindInstanceIndexAttribute(instanceIndexBuffer: StreamingIntBufferObject?)
    {
        val indexBuffer = instanceIndexBuffer ?: throw IllegalStateException("Instance index buffer is required for INSTANCE_ATTRIBUTE mode")
        indexBuffer.bind()
        glEnableVertexAttribArray(INSTANCE_INDEX_ATTRIBUTE_LOCATION)
        glVertexAttribIPointer(INSTANCE_INDEX_ATTRIBUTE_LOCATION, 1, GL_UNSIGNED_INT, Int.SIZE_BYTES, indexBuffer.getSubmittedDataByteOffset())
        glVertexAttribDivisor(INSTANCE_INDEX_ATTRIBUTE_LOCATION, 1)
    }

    private fun getIndirectCommandBuffer(): StreamingIntBufferObject =
        indirectCommandBuffer 
            ?: StreamingIntBufferObject
                .createDrawIndirectBuffer(initCapacity = INDIRECT_COMMAND_INTS * 256)
                .also { indirectCommandBuffer = it }
}

/**
 * Describes how a model vertex shader resolves its index into the per-frame model instance buffer.
 */
enum class ModelInstanceIndexMode
{
    /**
     * Preferred path when the driver exposes base-instance draws and shader draw parameters.
     * The shader reads `gl_BaseInstance + gl_InstanceID`, so each batch can select its first model
     * instance without a per-batch uniform update or an extra instanced vertex attribute.
     */
    BASE_INSTANCE,

    /**
     * Fallback path when base-instance draws are available, but shader draw parameters are not.
     * A per-instance integer attribute at location 6 supplies the model instance index. The draw call
     * still uses `baseInstance` so OpenGL offsets the instanced attribute stream for each batch.
     */
    INSTANCE_ATTRIBUTE,

    /**
     * Most compatible fallback path when base-instance draws are unavailable.
     * The renderer updates `uInstanceOffset` for each batch, and the shader resolves the model instance as
     * `uInstanceOffset + gl_InstanceID`. This costs one uniform update per batch, but works on older GL paths.
     */
    UNIFORM_OFFSET;
}

fun getSupportedModelInstanceIndexMode(): ModelInstanceIndexMode = when
{
    GlCapabilities.baseInstance &&
    GlCapabilities.shaderDrawParameters -> BASE_INSTANCE
    GlCapabilities.baseInstance -> INSTANCE_ATTRIBUTE
    else -> UNIFORM_OFFSET
}

/**
 * Patches a model vertex shader so it can index the GPU model instance buffer through `MODEL_INSTANCE_INDEX`.
 *
 * Model shaders are authored against the abstract `MODEL_INSTANCE_INDEX` macro. At load time this function
 * chooses the best supported [ModelInstanceIndexMode], inserts the needed feature macros after the `#version`
 * line, and defines `MODEL_INSTANCE_INDEX` for that path. On GL 4.6 shader-draw-parameters-capable drivers it
 * also upgrades the version line to `#version 460 core` so `gl_BaseInstance` can be used directly.
 */
fun transformModelVertexShader(source: String): String
{
    val newLineIndex = source.indexOf('\n')
    if (newLineIndex < 0)
        return source

    val mode = getSupportedModelInstanceIndexMode()
    val versionLine = if (mode == BASE_INSTANCE && GlCapabilities.shaderDrawParametersCore)
        "#version 460 core"
    else
        source.take(newLineIndex).trimEnd('\r')

    val drawIndexHeader = when (mode)
    {
        BASE_INSTANCE ->
        {
            val instanceIndex = if (GlCapabilities.shaderDrawParametersCore)
                "int(gl_BaseInstance) + gl_InstanceID"
            else
                "int(gl_BaseInstanceARB) + gl_InstanceID"

            val extension = if (GlCapabilities.shaderDrawParametersCore) 
                "" 
            else 
                "#extension GL_ARB_shader_draw_parameters : require\n"
            
            "$extension#define MODEL_INSTANCE_DRAW_INDEX ($instanceIndex)\n"
        }

        INSTANCE_ATTRIBUTE -> 
            "#define USE_INSTANCE_INDEX_ATTRIBUTE 1\n" +
            "#define MODEL_INSTANCE_DRAW_INDEX int(aInstanceIndex)\n"

        UNIFORM_OFFSET -> 
            "#define USE_INSTANCE_OFFSET_UNIFORM 1\n" +
            "#define MODEL_INSTANCE_DRAW_INDEX (uInstanceOffset + gl_InstanceID)\n"
    }

    val visibleInstanceHeader = """
        layout(std430, binding = ${GpuModelCuller.VISIBLE_INSTANCE_BUFFER_BINDING}) readonly buffer VisibleInstanceBuffer
        {
            uint uVisibleInstanceIndices[];
        };

        uniform bool uUseVisibleInstanceBuffer;

        int resolveModelInstanceIndex()
        {
            int drawIndex = MODEL_INSTANCE_DRAW_INDEX;
            return uUseVisibleInstanceBuffer ? int(uVisibleInstanceIndices[drawIndex]) : drawIndex;
        }

        #define MODEL_INSTANCE_INDEX resolveModelInstanceIndex()
    """.trimIndent()

    return versionLine + "\n" + drawIndexHeader + visibleInstanceHeader + source.substring(newLineIndex + 1)
}