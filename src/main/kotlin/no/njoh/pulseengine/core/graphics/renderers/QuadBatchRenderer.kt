package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.BufferObject
import no.njoh.pulseengine.core.graphics.api.objects.FloatBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.IntBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.shared.utils.BufferExtensions.putAll
import org.lwjgl.opengl.GL11.*

class QuadBatchRenderer(
    private val initialCapacity: Int,
    private val context: RenderContextInternal
) : BatchRenderer() {

    private lateinit var program: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: FloatBufferObject
    private lateinit var ebo: IntBufferObject

    private var vertexCount = 0
    private var singleVertexCount = 0

    override fun init()
    {
        vao = VertexArrayObject.createAndBind()

        val layout = VertexAttributeLayout()
            .withAttribute("position",3, GL_FLOAT)
            .withAttribute("color",1, GL_FLOAT)

        if (!this::program.isInitialized)
        {
            val capacity = initialCapacity * layout.strideInBytes * 4L
            vbo = BufferObject.createArrayBuffer(capacity)
            ebo = BufferObject.createElementBuffer(capacity / 6)
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/quad.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/quad.frag"
            )
        }

        vbo.bind()
        ebo.bind()
        program.bind()
        program.setVertexAttributeLayout(layout)
        vao.release()
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

    override fun onRenderBatch(surface: Surface2D, startIndex: Int, drawCount: Int)
    {
        vao.bind()
        ebo.bind()
        vbo.bind()

        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)

        glBindTexture(GL_TEXTURE_2D, 0)

        if (startIndex == 0)
        {
            vbo.submit()
            ebo.submit()
        }

        // 6 elements per quad, 4 bytes per element
        glDrawElements(GL_TRIANGLES, drawCount * 6, GL_UNSIGNED_INT, startIndex * 6L * 4)

        vao.release()
        vertexCount = 0
    }

    override fun cleanUp()
    {
        vbo.delete()
        vao.delete()
        ebo.delete()
        program.delete()
    }
}