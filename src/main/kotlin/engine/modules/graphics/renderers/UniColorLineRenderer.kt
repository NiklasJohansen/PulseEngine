package engine.modules.graphics.renderers

import engine.modules.graphics.*
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30

class UniColorLineRenderer(initialCapacity: Int, val gfxState: GraphicsState) : LineRendererInterface, BatchRenderer
{
    private val stride = 3 * java.lang.Float.BYTES
    private val bytes = stride * 2L * initialCapacity

    private lateinit var program: ShaderProgram
    private lateinit var vbo: FloatBufferObject
    private lateinit var vao: VertexArrayObject
    private var initialized = false
    private var rgbaColor: Float = 0f

    override fun init()
    {
        vao = VertexArrayObject.create()

        if (!initialized)
        {
            vbo = VertexBufferObject.create(bytes)
            program = ShaderProgram.create("/engine/shaders/default/lineUniColor.vert", "/engine/shaders/default/line.frag").use()
            initialized = true
        }

        vbo.bind()
        program.use()
        program.defineVertexAttributeArray("position", 3, GL30.GL_FLOAT, stride, 0)
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
        program.use()
        program.setUniform("projection", gfxState.projectionMatrix)
        program.setUniform("view", camera.viewMatrix)
        program.setUniform("model", gfxState.modelMatrix)
        program.setUniform("color", java.lang.Float.floatToIntBits(rgbaColor))

        vbo.draw(GL11.GL_LINES, 3)
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