package no.njoh.pulseengine.modules.graphics.renderers

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.*
import no.njoh.pulseengine.modules.graphics.objects.*
import no.njoh.pulseengine.util.BufferExtensions.putAll
import org.lwjgl.opengl.GL15.*

class TextureBatchRenderer(
    private val initialCapacity: Int,
    private val renderState: RenderState,
    private val textureArray: TextureArray
) : BatchRenderer {

    private lateinit var program: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: FloatBufferObject
    private lateinit var ebo: IntBufferObject

    private var vertexCount = 0

    override fun init()
    {
        vao = VertexArrayObject.createAndBind()

        val layout = VertexAttributeLayout()
            .withAttribute("position",3, GL_FLOAT)
            .withAttribute("offset", 2, GL_FLOAT)
            .withAttribute("rotation", 1, GL_FLOAT)
            .withAttribute("texCoord", 2, GL_FLOAT)
            .withAttribute("texIndex",1, GL_FLOAT)
            .withAttribute("color",1, GL_FLOAT)

        if (!this::program.isInitialized)
        {
            val vboCapacity = initialCapacity * NUM_VERTICES * layout.strideInBytes
            val eboCapacity = initialCapacity * NUM_INDICES * 4L
            vbo = BufferObject.createAndBindArrayBuffer(vboCapacity)
            ebo = BufferObject.createAndBindElementBuffer(eboCapacity)

            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/arrayTexture.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/arrayTexture.frag"
            )
        }

        vbo.bind()
        ebo.bind()
        program.bind()
        program.defineVertexAttributeArray(layout)
        program.setUniform("textureArray", 0)
        vao.release()
    }

    fun drawTexture(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        val uMin = texture.uMin
        val vMin = texture.vMin
        val uMax = texture.uMax
        val vMax = texture.vMax
        val texIndex = texture.id.toFloat()
        val xOffset = w * xOrigin
        val yOffset = h * yOrigin
        val rgba = renderState.rgba
        val depth = renderState.depth

        vbo.fill(40) {
            putAll(x, y, depth, -xOffset, -yOffset, rot, uMin, vMin, texIndex, rgba)
            putAll(x, y, depth, -xOffset, h-yOffset,  rot, uMin, vMax, texIndex, rgba)
            putAll(x, y, depth, w-xOffset, h-yOffset, rot, uMax, vMax, texIndex, rgba)
            putAll(x, y, depth, w-xOffset, -yOffset,  rot, uMax, vMin, texIndex, rgba)
        }

        val vc = vertexCount
        ebo.fill(6) {
            putAll(vc, vc + 1, vc + 2, vc + 2, vc + 3, vc)
        }

        vertexCount += NUM_VERTICES
        renderState.increaseDepth()
    }

    fun drawTexture(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        val uMax = texture.uMax * uMax
        val vMax = texture.vMax * vMax
        val uMin = texture.uMax * uMin
        val vMin = texture.vMax * vMin
        val index = texture.id.toFloat()
        val xOffset = w * xOrigin
        val yOffset = h * yOrigin
        val rgba = renderState.rgba
        val depth = renderState.depth

        vbo.fill(40) {
            putAll(x, y, depth, -xOffset, -yOffset, rot, uMin, vMin, index, rgba)
            putAll(x, y, depth, -xOffset, h-yOffset, rot, uMin, vMax, index, rgba)
            putAll(x, y, depth, w-xOffset, h-yOffset, rot, uMax, vMax, index, rgba)
            putAll(x, y, depth, w-xOffset, -yOffset, rot, uMax, vMin, index, rgba)
        }

        val vc = vertexCount
        ebo.fill(NUM_INDICES) {
            putAll(vc, vc + 1, vc + 2, vc + 2, vc + 3, vc)
        }

        vertexCount += NUM_VERTICES
        renderState.increaseDepth()
    }

    override fun render(surface: Surface2D)
    {
        if (vertexCount == 0)
            return

        vao.bind()
        vbo.bind()
        ebo.bind()
        program.bind()
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", surface.camera.viewMatrix)

        textureArray.bind()

        vbo.submit()
        ebo.submit()
        ebo.draw(GL_TRIANGLES, NUM_INDICES)

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

    companion object
    {
        private const val NUM_VERTICES = 4
        private const val NUM_INDICES = 6
    }
}