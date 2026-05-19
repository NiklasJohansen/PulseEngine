package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.graphics.api.ModelBatch
import no.njoh.pulseengine.core.graphics.api.ModelBatchList
import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.util.ModelInstanceIndexMode.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_LINES
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL15.GL_STREAM_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glGenBuffers
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
    private var indirectCommandBuffer = BufferUtils.createByteBuffer(INDIRECT_COMMAND_STRIDE_BYTES * 256)
    private var indirectCommandBufferId = 0

    fun drawTriangleIndices(vao: VertexArrayObject, firstIndex: Int, indexCount: Int)
    {
        vao.bind()
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, firstIndex.toLong() * Int.SIZE_BYTES)
        vao.release()
        GpuProfiler.incrementTriangles(indexCount / 3L)
        GpuProfiler.incrementDrawCalls()
    }

    fun drawTriangleVertices(vao: VertexArrayObject, firstVertexIndex: Int, vertexCount: Int)
    {
        vao.bind()
        glDrawArrays(GL_TRIANGLES, firstVertexIndex, vertexCount)
        vao.release()
        GpuProfiler.incrementTriangles(vertexCount / 3L)
        GpuProfiler.incrementDrawCalls()
    }

    fun drawTriangleStripVertices(vao: VertexArrayObject, firstVertex: Int, vertexCount: Int)
    {
        vao.bind()
        glDrawArrays(GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        vao.release()
        GpuProfiler.incrementTriangles(max(0, vertexCount - 2L))
        GpuProfiler.incrementDrawCalls()
    }

    fun drawInstancedTriangleStripVertices(vao: VertexArrayObject, firstVertex: Int, vertexCount: Int, instanceCount: Int)
    {
        vao.bind()
        glDrawArraysInstanced(GL_TRIANGLE_STRIP, firstVertex, vertexCount, instanceCount)
        vao.release()
        GpuProfiler.incrementTriangles(instanceCount * max(0, vertexCount - 2L))
        GpuProfiler.incrementDrawCalls()
    }

    fun drawLineVertices(vao: VertexArrayObject, firstVertex: Int, vertexCount: Int)
    {
        vao.bind()
        glDrawArrays(GL_LINES, firstVertex, vertexCount)
        vao.release()
        GpuProfiler.incrementDrawCalls()
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
        GpuProfiler.incrementTriangles(instanceCount * 2L)
        GpuProfiler.incrementDrawCalls()
    }

    fun drawModelBatches(
        batches: ModelBatchList,
        instanceIndexMode: ModelInstanceIndexMode,
        instanceIndexBuffer: StreamingIntBufferObject?
    ) {
        ModelBatch.resetBoundProgramAndCullMode()
        
        if (!GlCapabilities.multiDrawIndirect || instanceIndexMode == UNIFORM_OFFSET)
        {
            batches.forEach { it.drawDirect(instanceIndexMode, instanceIndexBuffer) }
            return
        }

        var groupStart = null as ModelBatch?
        var groupVao = null as VertexArrayObject?
        var commandCount = 0
        var triangleCount = 0L

        fun flushGroup()
        {
            val firstBatch = groupStart ?: return
            val vao = groupVao ?: return

            indirectCommandBuffer.flip()
            firstBatch.bindProgramAndSetCullMode()
            vao.bind()

            if (instanceIndexMode == INSTANCE_ATTRIBUTE)
                bindInstanceIndexAttribute(instanceIndexBuffer)

            submitIndirectCommands()
            glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0L, commandCount, 0)
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0)

            if (instanceIndexMode == INSTANCE_ATTRIBUTE)
                instanceIndexBuffer?.release()

            vao.release()
            GpuProfiler.incrementTriangles(triangleCount)
            GpuProfiler.incrementDrawCalls()

            groupStart = null
            groupVao = null
            commandCount = 0
            triangleCount = 0L
            indirectCommandBuffer.clear()
        }

        batches.forEach { batch ->

            val vao = batch.model.vao ?: return@forEach
            val firstBatch = groupStart

            if (firstBatch == null)
            {
                groupStart = batch
                groupVao = vao
            }
            else if (firstBatch.program !== batch.program || firstBatch.cullMode != batch.cullMode || groupVao !== vao)
            {
                flushGroup()
                groupStart = batch
                groupVao = vao
            }

            ensureIndirectCommandCapacity(commandCount + 1)
            putIndirectCommand(
                count = batch.subMesh.indexCount,
                instanceCount = batch.instanceCount,
                firstIndex = batch.subMesh.indexStart,
                baseVertex = 0,
                baseInstance = batch.instanceIndex
            )
            commandCount++
            triangleCount += batch.instanceCount * (batch.subMesh.indexCount / 3L)
        }

        flushGroup()
    }

    private fun ModelBatch.drawDirect(instanceIndexMode: ModelInstanceIndexMode, instanceIndexBuffer: StreamingIntBufferObject?)
    {
        val vao = model.vao ?: return
        bindProgramAndSetCullMode()
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
        GpuProfiler.incrementTriangles(instanceCount * (indexCount / 3L))
        GpuProfiler.incrementDrawCalls()
    }
    
    private const val INSTANCE_INDEX_ATTRIBUTE_LOCATION = 6
    private const val INDIRECT_COMMAND_STRIDE_BYTES = 5 * Int.SIZE_BYTES

    private fun bindInstanceIndexAttribute(instanceIndexBuffer: StreamingIntBufferObject?)
    {
        val indexBuffer = instanceIndexBuffer ?: throw IllegalStateException("Instance index buffer is required for INSTANCE_ATTRIBUTE mode")
        indexBuffer.bind()
        glEnableVertexAttribArray(INSTANCE_INDEX_ATTRIBUTE_LOCATION)
        glVertexAttribIPointer(INSTANCE_INDEX_ATTRIBUTE_LOCATION, 1, GL_UNSIGNED_INT, Int.SIZE_BYTES, indexBuffer.getSubmittedDataByteOffset())
        glVertexAttribDivisor(INSTANCE_INDEX_ATTRIBUTE_LOCATION, 1)
    }

    private fun ensureIndirectCommandCapacity(commandCount: Int)
    {
        val requiredBytes = commandCount * INDIRECT_COMMAND_STRIDE_BYTES
        if (requiredBytes <= indirectCommandBuffer.capacity())
            return

        val newCapacity = max(requiredBytes, indirectCommandBuffer.capacity() * 2)
        val newBuffer = BufferUtils.createByteBuffer(newCapacity)
        indirectCommandBuffer.flip()
        newBuffer.put(indirectCommandBuffer)
        indirectCommandBuffer = newBuffer
    }

    private fun putIndirectCommand(count: Int, instanceCount: Int, firstIndex: Int, baseVertex: Int, baseInstance: Int)
    {
        indirectCommandBuffer
            .putInt(count)
            .putInt(instanceCount)
            .putInt(firstIndex)
            .putInt(baseVertex)
            .putInt(baseInstance)
    }

    private fun submitIndirectCommands()
    {
        if (indirectCommandBufferId == 0)
            indirectCommandBufferId = glGenBuffers()

        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectCommandBufferId)
        glBufferData(GL_DRAW_INDIRECT_BUFFER, indirectCommandBuffer, GL_STREAM_DRAW)
    }
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

    val header = when (mode)
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
            
            "$extension#define MODEL_INSTANCE_INDEX ($instanceIndex)\n"
        }

        INSTANCE_ATTRIBUTE -> 
            "#define USE_INSTANCE_INDEX_ATTRIBUTE 1\n" +
            "#define MODEL_INSTANCE_INDEX int(aInstanceIndex)\n"

        UNIFORM_OFFSET -> 
            "#define USE_INSTANCE_OFFSET_UNIFORM 1\n" +
            "#define MODEL_INSTANCE_INDEX (uInstanceOffset + gl_InstanceID)\n"
    }

    return versionLine + "\n" + header + source.substring(newLineIndex + 1)
}