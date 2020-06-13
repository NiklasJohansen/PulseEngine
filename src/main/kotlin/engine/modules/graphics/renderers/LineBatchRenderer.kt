package engine.modules.graphics.renderers

import engine.modules.graphics.*
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
            vbo = BufferObject.createAndBind(initialCapacity * layout.stride * 2L)
            program = ShaderProgram
                .create("/engine/shaders/default/line.vert", "/engine/shaders/default/line.frag")
                .bind()
        }

        vbo.bind()
        program.bind()
        program.defineVertexAttributeArray(layout)
        vao.release()
    }

    fun linePoint(x: Float, y: Float)
    {
        vbo.put(x, y, gfxState.depth, gfxState.rgba)
        gfxState.increaseDepth()
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        val depth = gfxState.depth
        val rgba = gfxState.rgba
        vbo.put(x0, y0, depth, rgba, x1, y1, depth, rgba)
        gfxState.increaseDepth()
    }

    override fun render(camera: CameraEngineInterface)
    {
        vao.bind()
        vbo.bind()
        program.bind()
        program.setUniform("projection", camera.projectionMatrix)
        program.setUniform("view", camera.viewMatrix)
        program.setUniform("model", camera.modelMatrix)

        vbo.flush()
        vbo.draw(GL_LINES, 4)

        vao.release()
    }

    override fun cleanup()
    {
        vbo.delete()
        vao.delete()
        program.delete()
    }
}