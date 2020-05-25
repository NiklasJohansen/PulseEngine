package engine.modules.graphics.renderers

import engine.modules.graphics.*
import org.lwjgl.opengl.GL11

class LineBatchRenderer(
    initialCapacity: Int,
    private val gfxState: GraphicsState
) : BatchRenderer {

    private val stride = 4 * java.lang.Float.BYTES
    private val bytes = 2L * initialCapacity * stride
    private lateinit var program: ShaderProgram
    private lateinit var vbo: FloatBufferObject
    private lateinit var vao: VertexArrayObject

    override fun init()
    {
        if (!this::vao.isInitialized)
        {
            vao = VertexArrayObject.create()
            vbo = VertexBufferObject.create(bytes)
            program = ShaderProgram.create("/engine/shaders/default/line.vert", "/engine/shaders/default/line.frag").use()
        }
        else
        {
            vao.delete()
            vao = VertexArrayObject.create()
        }

        vbo.bind()
        program.use()
        program.defineVertexAttributeArray("position", 3, GL11.GL_FLOAT, stride, 0)
        program.defineVertexAttributeArray("rgbaColor",1, GL11.GL_FLOAT, stride, 3 * java.lang.Float.BYTES)
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
        program.use()
        program.setUniform("projection", gfxState.projectionMatrix)
        program.setUniform("view", camera.viewMatrix)
        program.setUniform("model", gfxState.modelMatrix)

        vbo.draw(GL11.GL_LINES, 4)

        vao.release()
    }

    override fun cleanup()
    {
        vbo.delete()
        vao.delete()
        program.delete()
    }
}