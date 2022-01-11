package no.njoh.pulseengine.modules.graphics.renderers

import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.*
import no.njoh.pulseengine.modules.graphics.objects.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL31.glDrawArraysInstanced

class TextureBatchRenderer(
    private val initialCapacity: Int,
    private val renderState: RenderState,
    private val textureArray: TextureArray
) : BatchRenderer {

    private lateinit var vao: VertexArrayObject
    private lateinit var program: ShaderProgram
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var instanceBuffer: FloatBufferObject
    private var instanceCount = 0

    override fun init()
    {
        vao = VertexArrayObject.createAndBind()

        val vertexLayout = VertexAttributeLayout()
            .withAttribute("vertexPos", 2, GL_FLOAT)

        val instanceLayout = VertexAttributeLayout()
            .withAttribute("worldPos", 3, GL_FLOAT, 1)
            .withAttribute("size", 2, GL_FLOAT, 1)
            .withAttribute("origin", 2, GL_FLOAT, 1)
            .withAttribute("rotation", 1, GL_FLOAT, 1)
            .withAttribute("uvMin", 2, GL_FLOAT, 1)
            .withAttribute("uvMax", 2, GL_FLOAT, 1)
            .withAttribute("color", 1, GL_FLOAT, 1)
            .withAttribute("texIndex", 1, GL_FLOAT, 1)

        if (!this::program.isInitialized)
        {
            instanceBuffer = BufferObject.createAndBindArrayBuffer(initialCapacity * instanceLayout.strideInBytes)
            vertexBuffer = StaticBufferObject.createAndBindBuffer(floatArrayOf(
                0f, 0f, // Top-left vertex
                1f, 0f, // Top-right vertex
                0f, 1f, // Bottom-left vertex
                1f, 1f  // Bottom-right vertex
            ))
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/default/arrayTexture.vert",
                fragmentShaderFileName = "/pulseengine/shaders/default/arrayTexture.frag"
            )
        }

        program.bind()
        vertexBuffer.bind()
        program.defineVertexAttributeLayout(vertexLayout)
        instanceBuffer.bind()
        program.defineVertexAttributeLayout(instanceLayout)
        vao.release()
    }

    fun drawTexture(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float)
    {
        instanceBuffer.fill(14)
        {
            put(x)
            put(y)
            put(renderState.depth)
            put(w)
            put(h)
            put(xOrigin)
            put(yOrigin)
            put(rot)
            put(texture.uMin)
            put(texture.vMin)
            put(texture.uMax)
            put(texture.vMax)
            put(renderState.rgba)
            put(texture.id.toFloat())
        }

        instanceCount++
        renderState.increaseDepth()
    }

    fun drawTexture(texture: Texture, x: Float, y: Float, w: Float, h: Float, rot: Float, xOrigin: Float, yOrigin: Float, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        instanceBuffer.fill(14)
        {
            put(x)
            put(y)
            put(renderState.depth)
            put(w)
            put(h)
            put(xOrigin)
            put(yOrigin)
            put(rot)
            put(texture.uMax * uMin)
            put(texture.vMax * vMin)
            put(texture.uMax * uMax)
            put(texture.vMax * vMax)
            put(renderState.rgba)
            put(texture.id.toFloat())
        }

        instanceCount++
        renderState.increaseDepth()
    }

    override fun render(surface: Surface2D)
    {
        if (instanceCount == 0)
            return

        // Submit per-instance data to GPU
        instanceBuffer.bind()
        instanceBuffer.submit()
        instanceBuffer.release()

        // Bind VAO with buffers and attribute layout
        vao.bind()

        // Bind texture array to texture unit 0
        textureArray.bind(0)

        // Bind shader program and set uniforms
        program.bind()
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", surface.camera.viewMatrix)

        // Draw all instances
        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount)

        // Release VAO and reset count
        vao.release()
        instanceCount = 0
    }

    override fun cleanUp()
    {
        vertexBuffer.delete()
        instanceBuffer.delete()
        program.delete()
        vao.delete()
    }
}