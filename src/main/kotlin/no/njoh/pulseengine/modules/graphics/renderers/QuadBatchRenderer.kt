package no.njoh.pulseengine.modules.graphics.renderers

import no.njoh.pulseengine.modules.graphics.*
import org.lwjgl.opengl.GL11.*

class QuadBatchRenderer(
    private val initialCapacity: Int,
    private val gfxState: RenderState
) : BatchRenderer {

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
            val capacity = initialCapacity * layout.stride * 4L
            vbo = BufferObject.createAndBindArrayBuffer(capacity)
            ebo = BufferObject.createAndBindElementBuffer(capacity / 6)
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/default.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/default.frag"
            )
        }

        vbo.bind()
        ebo.bind()
        program.bind()
        program.defineVertexAttributeArray(layout)
        vao.release()
    }

    fun quad(x: Float, y: Float, width: Float, height: Float)
    {
        val depth = gfxState.depth
        val rgba = gfxState.rgba

        vbo.put(x, y, depth, rgba, x, y + height, depth, rgba)
        vbo.put(x + width, y + height, depth, rgba, x + width, y, depth, rgba)

        ebo.put(
            vertexCount + 0,
            vertexCount + 1,
            vertexCount + 2,
            vertexCount + 2,
            vertexCount + 3,
            vertexCount + 0
        )

        vertexCount += 4
        gfxState.increaseDepth()
    }

    fun vertex(x: Float, y: Float)
    {
        vbo.put(x, y, gfxState.depth, gfxState.rgba)
        singleVertexCount++

        if (singleVertexCount == 4)
        {
            ebo.put(
                vertexCount + 0,
                vertexCount + 1,
                vertexCount + 2,
                vertexCount + 2,
                vertexCount + 3,
                vertexCount + 0
            )
            singleVertexCount = 0
            vertexCount += 4
            gfxState.increaseDepth()
        }
    }

    override fun render(surface: Surface2D)
    {
        if (vertexCount == 0)
            return

        vao.bind()
        ebo.bind()
        vbo.bind()

        program.bind()
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", surface.camera.viewMatrix)

        glBindTexture(GL_TEXTURE_2D, 0)

        vbo.flush()
        ebo.flush()
        ebo.draw(GL_TRIANGLES, 1)

        vertexCount = 0
        vao.release()
    }

    override fun cleanUp()
    {
        vbo.delete()
        vao.delete()
        ebo.delete()
        program.delete()
    }
}