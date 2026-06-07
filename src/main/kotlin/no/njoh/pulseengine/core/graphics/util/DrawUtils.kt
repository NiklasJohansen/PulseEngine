package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.graphics.api.world.RenderItemBatch
import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.graphics.api.objects.StreamingIntBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderBucket
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderCommandBuilder
import no.njoh.pulseengine.core.graphics.api.world.DrawPayload.DirectDrawPayload
import no.njoh.pulseengine.core.graphics.api.world.DrawPayload.EmptyDrawPayload
import no.njoh.pulseengine.core.graphics.api.world.DrawPayload.IndirectDrawPayload
import no.njoh.pulseengine.core.graphics.api.world.PreparedRenderPass
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.captureIndirectDrawStats
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.incrementDrawStats
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.incrementWorldInstances
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

    fun drawWorldRenderBucket(bucket: WorldRenderBucket, preparedPass: PreparedRenderPass, programs: ShaderProgramSet, cullViewIndex: Int = 0) 
    {
        if (bucket.size == 0 || cullViewIndex >= preparedPass.cullViewCount) return

        when (val payload = preparedPass.drawPayload)
        {
            is EmptyDrawPayload    -> return
            is DirectDrawPayload   -> drawDirectWorldRenderBucket(bucket, programs, payload)
            is IndirectDrawPayload -> drawIndirectWorldRenderBucket(bucket, programs, payload, cullViewIndex)
        }
    }

    private fun drawIndirectWorldRenderBucket(bucket: WorldRenderBucket, programs: ShaderProgramSet, payload: IndirectDrawPayload, cullViewIndex: Int) 
    {
        RenderItemBatch.resetBoundProgramAndCullMode()

        if (payload.useVisibleInstanceBuffer)
            payload.visibleInstanceBuffer?.bindSubmittedRange()

        var groupStart = null as RenderItemBatch?
        var groupVao = null as VertexArrayObject?
        var groupCommandStart = bucket.commandStartIndex
        var commandIndex = bucket.commandStartIndex
        var commandCount = 0
        var triangleCount = 0L
        var instanceCount = 0L

        fun flushGroup()
        {
            val firstBatch = groupStart ?: return
            val vao = groupVao ?: return

            firstBatch.bindProgramAndSetCullMode(programs)
            programs[firstBatch.shaderVariant].setUniform("uUseVisibleInstanceBuffer", payload.useVisibleInstanceBuffer)
            vao.bind()

            if (!payload.useVisibleInstanceBuffer && payload.instanceIndexMode == INSTANCE_ATTRIBUTE)
                bindInstanceIndexAttribute(payload.instanceIndexBuffer)

            val commandByteOffset = payload.getCommandByteOffset(cullViewIndex, groupCommandStart)
 
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, payload.commandBuffer.id)
            glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, commandByteOffset, commandCount, 0)
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0)

            if (!payload.useVisibleInstanceBuffer && payload.instanceIndexMode == INSTANCE_ATTRIBUTE)
                payload.instanceIndexBuffer?.release()

            vao.release()

            if (payload.useVisibleInstanceBuffer)
            {
                captureIndirectDrawStats(payload.commandBuffer.id, commandByteOffset, commandCount)
            }
            else
            {
                incrementDrawStats(commandCount.toLong(), triangleCount, instanceCount)
                incrementWorldInstances(instanceCount)
            }

            groupStart = null
            groupVao = null
            commandCount = 0
            triangleCount = 0L
            instanceCount = 0L
        }

        bucket.forEachBatch { batch ->
            val vao = batch.mesh.vao
            if (vao == null)
            {
                flushGroup()
                commandIndex++
                return@forEachBatch
            }

            val firstBatch = groupStart

            if (firstBatch == null)
            {
                groupStart = batch
                groupVao = vao
                groupCommandStart = commandIndex
            }
            else if (firstBatch.shaderVariant != batch.shaderVariant || firstBatch.cullMode != batch.cullMode || groupVao !== vao)
            {
                flushGroup()
                groupStart = batch
                groupVao = vao
                groupCommandStart = commandIndex
            }

            commandCount++
            if (!payload.useVisibleInstanceBuffer)
            {
                triangleCount += batch.instanceCount * (batch.mesh.indexCount / 3L)
                instanceCount += batch.instanceCount
            }
            commandIndex++
        }

        flushGroup()
    }

    private fun drawDirectWorldRenderBucket(bucket: WorldRenderBucket, programs: ShaderProgramSet, payload: DirectDrawPayload)
    {
        RenderItemBatch.resetBoundProgramAndCullMode()
        bucket.forEachBatch()
        {
            val vao = it.mesh.vao ?: return
            val program = programs[it.shaderVariant]
            it.bindProgramAndSetCullMode(programs)
            program.setUniform("uUseVisibleInstanceBuffer", false)
            drawInstancedTriangleIndices(
                program = program,
                vao = vao,
                instanceIndexMode = payload.instanceIndexMode,
                instanceIndexBuffer = payload.instanceIndexBuffer,
                firstIndex = it.mesh.indexStart,
                indexCount = it.mesh.indexCount,
                instanceIndex = it.instanceIndex,
                instanceCount = it.instanceCount,
                baseVertex = 0
            )
            incrementWorldInstances(it.instanceCount.toLong())
        }
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

    private fun bindInstanceIndexAttribute(instanceIndexBuffer: StreamingIntBufferObject?)
    {
        val indexBuffer = instanceIndexBuffer ?: throw IllegalStateException("Instance index buffer is required for INSTANCE_ATTRIBUTE mode")
        indexBuffer.bind()
        glEnableVertexAttribArray(INSTANCE_INDEX_ATTRIBUTE_LOCATION)
        glVertexAttribIPointer(INSTANCE_INDEX_ATTRIBUTE_LOCATION, 1, GL_UNSIGNED_INT, Int.SIZE_BYTES, indexBuffer.getSubmittedDataByteOffset())
        glVertexAttribDivisor(INSTANCE_INDEX_ATTRIBUTE_LOCATION, 1)
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

    val drawIndexHeader = when (mode)
    {
        BASE_INSTANCE ->
        {
            val instanceIndex = if (GlCapabilities.shaderDrawParametersCore)
                "uint(gl_BaseInstance) + uint(gl_InstanceID)"
            else
                "uint(gl_BaseInstanceARB) + uint(gl_InstanceID)"

            val extension = if (GlCapabilities.shaderDrawParametersCore) 
                "" 
            else 
                "#extension GL_ARB_shader_draw_parameters : require\n"
            
            "$extension#define MODEL_INSTANCE_DRAW_INDEX ($instanceIndex)\n"
        }

        INSTANCE_ATTRIBUTE -> 
            "#define USE_INSTANCE_INDEX_ATTRIBUTE 1\n" +
            "#define MODEL_INSTANCE_DRAW_INDEX aInstanceIndex\n"

        UNIFORM_OFFSET -> 
            "#define USE_INSTANCE_OFFSET_UNIFORM 1\n" +
            "#define MODEL_INSTANCE_DRAW_INDEX (uint(uInstanceOffset) + uint(gl_InstanceID))\n"
    }

    val visibleInstanceHeader = """
        layout(std430, binding = ${WorldRenderCommandBuilder.VISIBLE_INSTANCE_BUFFER_BINDING}) readonly buffer VisibleInstanceBuffer
        {
            uint uVisibleInstanceIndices[];
        };

        uniform bool uUseVisibleInstanceBuffer;

        uint resolveModelInstanceIndex()
        {
            uint drawIndex = MODEL_INSTANCE_DRAW_INDEX;
            return uUseVisibleInstanceBuffer ? uVisibleInstanceIndices[int(drawIndex)] : drawIndex;
        }

        #define MODEL_INSTANCE_INDEX resolveModelInstanceIndex()
    """.trimIndent()

    val transformedSource = source.substring(newLineIndex + 1)

    return versionLine + "\n" + drawIndexHeader + visibleInstanceHeader + transformedSource
}
