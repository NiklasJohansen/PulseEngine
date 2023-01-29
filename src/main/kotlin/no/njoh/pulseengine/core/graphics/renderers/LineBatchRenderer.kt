package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.BufferObject
import no.njoh.pulseengine.core.graphics.api.objects.FloatBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.shared.utils.BufferExtensions.putAll
import org.lwjgl.opengl.GL11.*

class LineBatchRenderer(
    private val initialCapacity: Int,
    private val context: RenderContextInternal
) : BatchRenderer() {

    private lateinit var program: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: FloatBufferObject
    private var vertices = 0

    override fun init()
    {
        vao = VertexArrayObject.createAndBind()

        val layout = VertexAttributeLayout()
            .withAttribute("position",3, GL_FLOAT)
            .withAttribute("rgbaColor",1, GL_FLOAT)

        if (!this::program.isInitialized)
        {
            vbo = BufferObject.createArrayBuffer(initialCapacity * layout.strideInBytes * 2L)
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/line.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/line.frag"
            )
        }

        vbo.bind()
        program.bind()
        program.setVertexAttributeLayout(layout)
        vao.release()
    }

    fun lineVertex(x: Float, y: Float)
    {
        vbo.fill(4)
        {
            putAll(x, y, context.depth, context.drawColor)
        }
        context.increaseDepth()
        vertices++
        if (vertices == 2)
        {
            vertices = 0
            increaseBatchSize()
        }
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        val depth = context.depth
        val rgba = context.drawColor
        vbo.fill(8)
        {
            putAll(x0, y0, depth, rgba)
            putAll(x1, y1, depth, rgba)
        }
        context.increaseDepth()
        increaseBatchSize()
    }

    override fun onRenderBatch(surface: Surface2D, startIndex: Int, drawCount: Int) 
    {
        vao.bind()
        vbo.bind()
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)

        // Submit data to GPU in first batch
        if (startIndex == 0)
            vbo.submit()

        glDrawArrays(GL_LINES, startIndex * 2, drawCount * 2) // 2 vertices per line

        vao.release()
    }
    
    override fun cleanUp()
    {
        vbo.delete()
        vao.delete()
        program.delete()
    }
}