package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.shared.utils.BufferExtensions.putAll
import org.lwjgl.opengl.GL11.*

class QuadBatchRenderer(
    private val context: RenderContextInternal
) : BatchRenderer() {

    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: DoubleBufferedFloatObject
    private lateinit var ebo: DoubleBufferedIntObject
    private lateinit var program: ShaderProgram

    private var vertexCount = 0
    private var singleVertexCount = 0

    override fun init()
    {
        if (!this::program.isInitialized)
        {
            vbo = DoubleBufferedFloatObject.createArrayBuffer()
            ebo = DoubleBufferedIntObject.createElementBuffer()
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/quad.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/quad.frag"
            )
        }

        val layout = VertexAttributeLayout()
            .withAttribute("position", 3, GL_FLOAT)
            .withAttribute("color", 1, GL_FLOAT)

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        ebo.bind()
        program.bind()
        program.setVertexAttributeLayout(layout)
        vao.release()
    }

    override fun onInitFrame()
    {
        vbo.swapBuffers()
        ebo.swapBuffers()
        vertexCount = 0
    }

    override fun onRenderBatch(surface: Surface2D, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            vbo.bind()
            vbo.submit()
            ebo.bind()
            ebo.submit()
        }

        vao.bind()
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        glBindTexture(GL_TEXTURE_2D, 0)
        // 6 elements per quad, 4 bytes per element
        glDrawElements(GL_TRIANGLES, drawCount * 6, GL_UNSIGNED_INT, startIndex * 6L * 4)
        vao.release()
    }

    override fun cleanUp()
    {
        vao.delete()
        vbo.delete()
        ebo.delete()
        program.delete()
    }

    fun quad(x: Float, y: Float, width: Float, height: Float)
    {
        val depth = context.depth
        val rgba = context.drawColor

        vbo.fill(16)
        {
            putAll(x, y, depth, rgba)
            putAll(x, y + height, depth, rgba)
            putAll(x + width, y + height, depth, rgba)
            putAll(x + width, y, depth, rgba)
        }

        ebo.fill(6)
        {
            putAll(vertexCount + 0, vertexCount + 1, vertexCount + 2)
            putAll(vertexCount + 2, vertexCount + 3, vertexCount + 0)
        }

        vertexCount += 4
        context.increaseDepth()
        increaseBatchSize()
    }

    fun vertex(x: Float, y: Float)
    {
        vbo.fill(4)
        {
            putAll(x, y, context.depth, context.drawColor)
        }

        singleVertexCount++

        if (singleVertexCount == 4)
        {
            ebo.fill(6)
            {
                putAll(vertexCount + 0, vertexCount + 1, vertexCount + 2)
                putAll(vertexCount + 2, vertexCount + 3, vertexCount + 0)
            }
            vertexCount += 4
            singleVertexCount = 0
            context.increaseDepth()
            increaseBatchSize()
        }
    }
}