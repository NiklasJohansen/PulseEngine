package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.graphics.api.GlCapabilities
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import org.lwjgl.opengl.GL11.GL_LINES
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL31.glDrawArraysInstanced
import org.lwjgl.opengl.GL42.glDrawArraysInstancedBaseInstance
import kotlin.math.max

object DrawUtils
{
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
        instanceBuffer: DoubleBufferedFloatObject,
        attributeLayout: VertexAttributeLayout,
        shaderProgram: ShaderProgram,
        instanceCount: Int,
        baseInstanceIndex: Int
    ) {
        if (GlCapabilities.baseInstance)
        {
            glDrawArraysInstancedBaseInstance(GL_TRIANGLE_STRIP, 0, 4, instanceCount, baseInstanceIndex)
        }
        else // Fall back to glDrawArraysInstanced (macOS)
        {
            instanceBuffer.bind()
            attributeLayout.bind(shaderProgram, baseInstanceIndex)
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount)
        }
        GpuProfiler.incrementTriangles(instanceCount * 2L)
        GpuProfiler.incrementDrawCalls()
    }
}