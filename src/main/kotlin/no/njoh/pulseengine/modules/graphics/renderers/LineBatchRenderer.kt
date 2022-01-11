package no.njoh.pulseengine.modules.graphics.renderers

import no.njoh.pulseengine.modules.graphics.*
import no.njoh.pulseengine.modules.graphics.objects.BufferObject
import no.njoh.pulseengine.modules.graphics.objects.FloatBufferObject
import no.njoh.pulseengine.modules.graphics.objects.VertexArrayObject
import no.njoh.pulseengine.util.BufferExtensions.putAll
import org.lwjgl.opengl.GL11.*

class LineBatchRenderer(
    private val initialCapacity: Int,
    private val gfxState: RenderState
) : BatchRenderer {

    private lateinit var program: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: FloatBufferObject

    override fun init()
    {
        vao = VertexArrayObject.createAndBind()

        val layout = VertexAttributeLayout()
            .withAttribute("position",3, GL_FLOAT)
            .withAttribute("rgbaColor",1, GL_FLOAT)

        if (!this::program.isInitialized)
        {
            vbo = BufferObject.createAndBindArrayBuffer(initialCapacity * layout.strideInBytes * 2L)
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/line.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/line.frag"
            )
        }

        vbo.bind()
        program.bind()
        program.defineVertexAttributeLayout(layout)
        vao.release()
    }

    fun lineVertex(x: Float, y: Float)
    {
        vbo.fill(4)
        {
            putAll(x, y, gfxState.depth, gfxState.rgba)
        }
        gfxState.increaseDepth()
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        val depth = gfxState.depth
        val rgba = gfxState.rgba
        vbo.fill(8)
        {
            putAll(x0, y0, depth, rgba)
            putAll(x1, y1, depth, rgba)
        }
        gfxState.increaseDepth()
    }

    override fun render(surface: Surface2D)
    {
        vao.bind()
        vbo.bind()
        program.bind()
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", surface.camera.viewMatrix)

        vbo.submit()
        vbo.draw(GL_LINES, 4)

        vao.release()
    }

    override fun cleanUp()
    {
        vbo.delete()
        vao.delete()
        program.delete()
    }
}