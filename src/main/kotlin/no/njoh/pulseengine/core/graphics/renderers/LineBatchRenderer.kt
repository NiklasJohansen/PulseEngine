package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.shared.utils.BufferExtensions.putAll
import org.lwjgl.opengl.GL11.*

class LineBatchRenderer(
    private val config: SurfaceConfigInternal
) : BatchRenderer() {

    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: DoubleBufferedFloatObject
    private lateinit var program: ShaderProgram
    private var vertices = 0

    override fun init()
    {
        if (!this::program.isInitialized)
        {
            vbo = DoubleBufferedFloatObject.createArrayBuffer()
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/line.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/line.frag"
            )
        }

        val layout = VertexAttributeLayout()
            .withAttribute("position", 3, GL_FLOAT)
            .withAttribute("rgbaColor", 1, GL_FLOAT)

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        program.bind()
        program.setVertexAttributeLayout(layout)
        vao.release()
    }

    override fun onInitFrame()
    {
        vbo.swapBuffers()
    }

    override fun onRenderBatch(surface: Surface, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            vbo.bind()
            vbo.submit()
            vbo.release()
        }

        vao.bind()
        program.bind()
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        glDrawArrays(GL_LINES, startIndex * 2, drawCount * 2) // 2 vertices per line
        vao.release()
    }

    override fun cleanUp()
    {
        vbo.delete()
        vao.delete()
        program.delete()
    }

    fun lineVertex(x: Float, y: Float)
    {
        vbo.fill(4)
        {
            putAll(x, y, config.currentDepth, config.currentDrawColor)
        }
        config.increaseDepth()
        vertices++
        if (vertices == 2)
        {
            vertices = 0
            increaseBatchSize()
        }
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        val depth = config.currentDepth
        val rgba = config.currentDrawColor
        vbo.fill(8)
        {
            putAll(x0, y0, depth, rgba)
            putAll(x1, y1, depth, rgba)
        }
        config.increaseDepth()
        increaseBatchSize()
    }
}