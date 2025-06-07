package no.njoh.pulseengine.modules.lighting.shared

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.*
import no.njoh.pulseengine.core.graphics.renderers.BatchRenderer
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import org.lwjgl.opengl.ARBBaseInstance.glDrawArraysInstancedBaseInstance
import org.lwjgl.opengl.GL20.*

class NormalMapRenderer(private val config: SurfaceConfigInternal) : BatchRenderer()
{
    private lateinit var vao: VertexArrayObject
    private lateinit var program: ShaderProgram
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var instanceBuffer: DoubleBufferedFloatObject

    override fun init(engine: PulseEngineInternal)
    {
        if (!this::program.isInitialized)
        {
            instanceBuffer = DoubleBufferedFloatObject.createArrayBuffer()
            vertexBuffer = StaticBufferObject.createQuadVertexArrayBuffer()
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/normal_map.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/normal_map.frag"))
            )
        }

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
            .withAttribute("textureHandle", 1, GL_FLOAT, 1)
            .withAttribute("normalScale", 2, GL_FLOAT, 1)

        vao = VertexArrayObject.createAndBind()
        program.bind()
        vertexBuffer.bind()
        program.setVertexAttributeLayout(vertexLayout)
        instanceBuffer.bind()
        program.setVertexAttributeLayout(instanceLayout)
        vao.release()
    }

    override fun onInitFrame()
    {
        instanceBuffer.swapBuffers()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: Surface, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            instanceBuffer.bind()
            instanceBuffer.submit()
            instanceBuffer.release()
        }

        vao.bind()
        program.bind()
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", surface.camera.viewMatrix)
        program.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        glDrawArraysInstancedBaseInstance(GL_TRIANGLE_STRIP, 0, 4, drawCount, startIndex)
        vao.release()
    }

    override fun destroy()
    {
        vertexBuffer.destroy()
        instanceBuffer.destroy()
        program.destroy()
        vao.destroy()
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
        orientation: Orientation = Orientation.NORMAL
    ) {
        instanceBuffer.fill(17)
        {
            put(x)
            put(y)
            put(config.currentDepth)
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
            put(texture?.handle?.toFloat() ?: -1f)
            put(normalScale * orientation.xDir)
            put(normalScale * orientation.yDir)
        }

        increaseBatchSize()
        config.increaseDepth()
    }

    enum class Orientation(val xDir: Float, val yDir: Float)
    {
        NORMAL(1f, 1f),
        INVERT_X(-1f, 1f),
        INVERT_Y(1f, -1f),
        INVERT_XY(-1f, -1f)
    }
}