package engine.modules.graphics.renderers

import engine.data.Texture
import engine.modules.graphics.*
import org.lwjgl.opengl.GL11

class TextureBatchRenderer(
    initialCapacity: Int,
    private val gfxState: GraphicsState
) : BatchRenderer {

    private val stride = 10 * java.lang.Float.BYTES
    private val bytes = initialCapacity * 4L * stride
    private var vertexCount = 0
    private lateinit var program: ShaderProgram
    private lateinit var vbo: FloatBufferObject
    private lateinit var ebo: IntBufferObject
    private lateinit var vao: VertexArrayObject

    override fun init()
    {
        if(!this::vao.isInitialized)
        {
            vao = VertexArrayObject.create()
            ebo = VertexBufferObject.createElementBuffer(bytes / 6)
            vbo = VertexBufferObject.create(bytes)
            program = ShaderProgram.create("/engine/shaders/default/arrayTexture.vert", "/engine/shaders/default/arrayTexture.frag").use()
        }
        else
        {
            vao.delete()
            vao = VertexArrayObject.create()
        }

        vbo.bind()
        program.use()
        program.defineVertexAttributeArray("position", 3, GL11.GL_FLOAT, stride, 0)
        program.defineVertexAttributeArray("offset", 2, GL11.GL_FLOAT, stride, 3 * java.lang.Float.BYTES)
        program.defineVertexAttributeArray("rotation", 1, GL11.GL_FLOAT, stride, 5 * java.lang.Float.BYTES)
        program.defineVertexAttributeArray("texCoord", 2, GL11.GL_FLOAT, stride, 6 * java.lang.Float.BYTES)
        program.defineVertexAttributeArray("texIndex",1, GL11.GL_FLOAT, stride, 8 * java.lang.Float.BYTES)
        program.defineVertexAttributeArray("color",1, GL11.GL_FLOAT, stride, 9 * java.lang.Float.BYTES)
        program.setUniform("textureArray", 0)
        vao.release()
    }

    fun drawTexture(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        val uMin = texture.uMin
        val vMin = texture.vMin
        val uMax = texture.uMax
        val vMax = texture.vMax
        val texIndex = texture.id.toFloat() + if(texture.format == GL11.GL_ALPHA) 0.5f else 0.0f
        val xOffset = w * xOrigin
        val yOffset = h * yOrigin
        val rgba = gfxState.rgba
        val depth = gfxState.depth

        vbo.put(
            x, y, depth, -xOffset, -yOffset,   rot, uMin, vMin, texIndex, rgba,
            x, y, depth, -xOffset, h-yOffset,  rot, uMin, vMax, texIndex, rgba,
            x, y, depth, w-xOffset, h-yOffset, rot, uMax, vMax, texIndex, rgba,
            x, y, depth, w-xOffset, -yOffset,  rot, uMax, vMin, texIndex, rgba
        )

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

    fun drawTexture(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        val uMax = texture.uMax * uMax
        val vMax = texture.vMax * vMax
        val uMin = texture.uMax * uMin
        val vMin = texture.vMax * vMin
        val index = texture.id.toFloat() + if(texture.format == GL11.GL_ALPHA) 0.5f else 0.0f
        val xOffset = w * xOrigin
        val yOffset = h * yOrigin
        val rgba = gfxState.rgba
        val depth = gfxState.depth

        vbo.put(
            x, y, depth, -xOffset, -yOffset,  rot, uMin, vMin, index, rgba,
            x, y, depth, -xOffset, h-yOffset, rot, uMin, vMax, index, rgba,
            x, y, depth, w-xOffset, h-yOffset, rot, uMax, vMax, index, rgba,
            x, y, depth, w-xOffset, -yOffset, rot, uMax, vMin, index, rgba
        )

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

    override fun render(camera: CameraEngineInterface)
    {
        if(vertexCount == 0)
            return

        vao.bind()
        vbo.bind()
        ebo.bind()
        program.use()
        program.setUniform("projection", gfxState.projectionMatrix)
        program.setUniform("view", camera.viewMatrix)
        program.setUniform("model", gfxState.modelMatrix)

        gfxState.textureArray.bind()

        vbo.flush()
        ebo.draw(GL11.GL_TRIANGLES, 1)

        vertexCount = 0
        vao.release()
    }

    override fun cleanup()
    {
        vbo.delete()
        vao.delete()
        ebo.delete()
        program.delete()
    }
}