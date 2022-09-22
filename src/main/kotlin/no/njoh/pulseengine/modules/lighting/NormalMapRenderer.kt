package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureArray
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer
import no.njoh.pulseengine.modules.lighting.NormalMapRenderer.Orientation.*
import org.lwjgl.opengl.ARBBaseInstance.glDrawArraysInstancedBaseInstance
import org.lwjgl.opengl.GL20.*

class NormalMapRenderer(
    private val initialCapacity: Int,
    private val context: RenderContextInternal,
    private val textureArray: TextureArray
) : BatchRenderer() {

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
            .withAttribute("tiling", 2, GL_FLOAT, 1)
            .withAttribute("textureIndex", 1, GL_FLOAT, 1)
            .withAttribute("normalScale", 2, GL_FLOAT, 1)

        if (!this::program.isInitialized)
        {
            instanceBuffer = BufferObject.createArrayBuffer(initialCapacity * instanceLayout.strideInBytes)
            vertexBuffer = StaticBufferObject.createBuffer(floatArrayOf(
                0f, 0f, // Top-left vertex
                1f, 0f, // Top-right vertex
                0f, 1f, // Bottom-left vertex
                1f, 1f  // Bottom-right vertex
            ))
            program = ShaderProgram.create(
                vertexShaderFileName = "/pulseengine/shaders/effects/normal_map.vert",
                fragmentShaderFileName = "/pulseengine/shaders/effects/normal_map.frag"
            )
        }

        program.bind()
        vertexBuffer.bind()
        program.setVertexAttributeLayout(vertexLayout)
        instanceBuffer.bind()
        program.setVertexAttributeLayout(instanceLayout)
        vao.release()
    }

    fun drawNormalMap(
        texture: Texture?,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rot: Float,
        xOrigin: Float,
        yOrigin: Float,
        uTiling: Float = 1f,
        vTiling: Float = 1f,
        normalScale: Float = 1f,
        orientation: Orientation = NORMAL
    ) {
        instanceBuffer.fill(17)
        {
            put(x)
            put(y)
            put(context.depth)
            put(w)
            put(h)
            put(xOrigin)
            put(yOrigin)
            put(rot)
            put(texture?.uMin ?: 0f)
            put(texture?.vMin ?: 0f)
            put(texture?.uMax ?: 1f)
            put(texture?.vMax ?: 1f)
            put(uTiling)
            put(vTiling)
            put(texture?.id?.toFloat() ?: -1f)
            put(normalScale * orientation.xDir)
            put(normalScale * orientation.yDir)
        }

        increaseBatchSize()
        context.increaseDepth()
    }

    override fun onRenderBatch(surface: Surface2D, startIndex: Int, drawCount: Int)
    {
        // Submit per-instance data to GPU
        if (startIndex == 0)
        {
            instanceBuffer.bind()
            instanceBuffer.submit()
            instanceBuffer.release()
        }

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
        glDrawArraysInstancedBaseInstance(GL_TRIANGLE_STRIP, 0, 4, drawCount, startIndex)

        // Release VAO and reset count
        vao.release()
    }

    override fun cleanUp()
    {
        vertexBuffer.delete()
        instanceBuffer.delete()
        program.delete()
        vao.delete()
    }

    enum class Orientation(val xDir: Float, val yDir: Float)
    {
        NORMAL(1f, 1f),
        INVERT_X(-1f, 1f),
        INVERT_Y(1f, -1f),
        INVERT_XY(-1f, -1f)
    }
}