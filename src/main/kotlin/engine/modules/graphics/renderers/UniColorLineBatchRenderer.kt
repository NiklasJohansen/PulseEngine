package engine.modules.graphics.renderers

import engine.modules.graphics.*
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30

class UniColorLineBatchRenderer(
    initialCapacity: Int,
    private val gfxState: GraphicsState
) : LineRendererInterface, BatchRenderer {

    private val stride = 3 * java.lang.Float.BYTES
    private val bytes = stride * 2L * initialCapacity
    private lateinit var program: ShaderProgram
    private lateinit var vbo: FloatBufferObject
    private lateinit var vao: VertexArrayObject
    private var rgbaColor: Float = 0f

    override fun init()
    {
        if (!this::vao.isInitialized)
        {
            vao = VertexArrayObject.createAndBind()
            vbo = VertexBufferObject.createAndBind(bytes)
            program = ShaderProgram.create("/engine/shaders/default/lineUniColor.vert", "/engine/shaders/default/line.frag").bind()
        }
        else
        {
            vao.delete()
            vao = VertexArrayObject.createAndBind()
        }

        vbo.bind()
        program.bind()
        program.defineVertexAttributeArray("position", 3, GL30.GL_FLOAT, stride, 0)
        vao.release()
    }

    override fun linePoint(x0: Float, y0: Float)
    {
        vbo.put(x0, y0, gfxState.depth)
        gfxState.increaseDepth()
    }

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        vbo.put(x0, y0, gfxState.depth, x1, y1, gfxState.depth)
        gfxState.increaseDepth()
    }

    override fun render(camera: CameraEngineInterface)
    {
        vao.bind()
        vbo.bind()
        program.bind()
        program.setUniform("projection", gfxState.projectionMatrix)
        program.setUniform("view", camera.viewMatrix)
        program.setUniform("model", gfxState.modelMatrix)
        program.setUniform("color", java.lang.Float.floatToIntBits(rgbaColor))

        vbo.flush()
        vbo.draw(GL11.GL_LINES, 3)

        vao.release()
    }

    fun setColor(rgba: Float)
    {
        this.rgbaColor = rgba
    }

    override fun cleanup()
    {
        vbo.delete()
        vao.delete()
        program.delete()
    }
}