package no.njoh.pulseengine.modules.lighting.shared

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.gpu.shader.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.gpu.buffer.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.StaticBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawInstancedQuads
import org.lwjgl.opengl.GL20.*

class NormalMapRenderer(
    private val config: SurfaceConfigInternal,
    override val order: Int = 50
) : Renderer() {

    private lateinit var vao: VertexArrayObject
    private lateinit var program: ShaderProgram
    private lateinit var vertexBuffer: StaticBufferObject
    private lateinit var instanceBuffer: DoubleBufferedFloatObject
    private lateinit var instanceLayout: VertexAttributeLayout

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        if (!this::program.isInitialized)
        {
            vertexBuffer = StaticBufferObject.createQuadVertexArrayBuffer()
            instanceBuffer = DoubleBufferedFloatObject.createArrayBuffer()
            instanceLayout = VertexAttributeLayout()
                .withAttribute("worldPos",  3, GL_FLOAT, 1)
                .withAttribute("size",      2, GL_FLOAT, 1)
                .withAttribute("origin",    2, GL_FLOAT, 1)
                .withAttribute("rotation",  1, GL_FLOAT, 1)
                .withAttribute("uvMin",     2, GL_FLOAT, 1)
                .withAttribute("uvMax",     2, GL_FLOAT, 1)
                .withAttribute("tiling",    2, GL_FLOAT, 1)
                .withAttribute("scale",     2, GL_FLOAT, 1)
                .withAttribute("texHandle", 1, GL_UNSIGNED_INT, 1)

            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/lighting/normal_map.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/lighting/normal_map.frag"))
            )
        }

        vao = VertexArrayObject.createAndBind()
        program.bind()
        vertexBuffer.bind()
        VertexAttributeLayout().withAttribute("vertexPos", 2, GL_FLOAT).bind(program)
        instanceBuffer.bind()
        instanceLayout.bind(program)
        vao.release()
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        instanceBuffer.swapBuffers()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            instanceBuffer.bind()
            instanceBuffer.submit()
            instanceBuffer.release()
        }

        program.bind()
        program.setUniform("cameraAngle", surface.camera.rotation.z)
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays(), filter = TextureFilter.LINEAR)
        drawInstancedQuads(vao, instanceBuffer, instanceLayout, program, startIndex, drawCount)
    }

    override fun destroy(engine: PulseEngineInternal)
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
        xTiling: Float = 1f,
        yTiling: Float = 1f,
        normalScale: Float = 1f,
        orientation: Orientation = Orientation.NORMAL
    ) {
        instanceBuffer.fill(17)
        {
            put(x, config.height - y, config.currentDepth)
            put(w, h)
            put(xOrigin, 1f - yOrigin)
            put(-rot)
            put(texture?.uMin ?: 0f)
            put(texture?.vMin ?: 0f)
            put(texture?.uMax ?: 1f)
            put(texture?.vMax ?: 1f)
            put(xTiling, yTiling)
            put(normalScale * orientation.xDir)
            put(normalScale * orientation.yDir)
            put(texture?.handle?.toFloat() ?: -1f)
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