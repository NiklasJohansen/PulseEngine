package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.gpu.buffer.DoubleBufferedFloatObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.VertexArrayObject
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.shader.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.surface.SurfaceConfigInternal
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawLineVertices
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import kotlin.math.cos
import kotlin.math.sin

class GizmoRenderer(
    var sceneSurfaceName: String,
    private val camera: Camera,
    private val config: SurfaceConfigInternal,
    override val order: Int = 60
) : Renderer() {

    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: DoubleBufferedFloatObject
    private lateinit var program: ShaderProgram

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        if (!this::program.isInitialized)
        {
            vbo = DoubleBufferedFloatObject.createArrayBuffer()
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/gizmo.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/gizmo.frag"))
            )
        }

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        program.bind()

        VertexAttributeLayout()
            .withAttribute("position",      3, GL_FLOAT)
            .withAttribute("rgbaColor",     1, GL_UNSIGNED_INT, integer = true)
            .withAttribute("pixelOffset",   2, GL_FLOAT)
            .withAttribute("dashDirection", 3, GL_FLOAT)
            .bind(program)

        vao.release()
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        vbo.swapBuffers()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex == 0)
        {
            vbo.bind()
            vbo.submit()
            vbo.release()
        }

        val sceneDepthTex = engine.gfx.getSurface(sceneSurfaceName)
            ?.takeIf { it !== surface && it.config.hasAttachment(DEPTH_TEXTURE) }
            ?.getTexture(DEPTH_TEXTURE, final = false)

        program.bind()
        program.setUniform("viewProjection", camera.viewProjectionMatrix)
        program.setUniform("screenSize", surface.config.width.toFloat(), surface.config.height.toFloat())
        program.setUniform("hasSceneDepth", sceneDepthTex != null)
        program.setUniform("viewportSize", surface.config.renderWidth.toFloat(), surface.config.renderHeight.toFloat())

        if (sceneDepthTex != null) 
            program.setUniformSampler("sceneDepth", sceneDepthTex, filter = NEAREST)

        val depthTest = glIsEnabled(GL_DEPTH_TEST)
        glDisable(GL_DEPTH_TEST)

        drawLineVertices(vao, startIndex * 2, drawCount * 2)

        if (depthTest) glEnable(GL_DEPTH_TEST)
    }

    fun drawLine(start: Vector3f, end: Vector3f, dashed: Boolean = false)
    {
        val color = config.currentDrawColor
        val dx = if (dashed) end.x - start.x else 0f
        val dy = if (dashed) end.y - start.y else 0f
        val dz = if (dashed) end.z - start.z else 0f
        vbo.fill(18)
        {
            put(start.x, start.y, start.z, color)
            put(0f, 0f)
            put(dx, dy, dz)
            put(end.x, end.y, end.z, color)
            put(0f, 0f)
            put(dx, dy, dz)
        }
        increaseBatchSize()
    }

    fun drawMarker(position: Vector3f, radius: Float, cross: Boolean = false)
    {
        val color = config.currentDrawColor
        var previousX = radius
        var previousY = 0f

        vbo.fill(16 * 18 + if (cross) 36 else 0)
        {
            for (i in 1..16)
            {
                val angle = i * (Math.PI.toFloat() * 2f / 16)
                val currentX = cos(angle) * radius
                val currentY = sin(angle) * radius
                put(position.x, position.y, position.z, color)
                put(previousX, previousY)
                put(0f, 0f, 0f)
                put(position.x, position.y, position.z, color)
                put(currentX, currentY)
                put(0f, 0f, 0f)
                previousX = currentX
                previousY = currentY
            }

            if (cross)
            {
                put(position.x, position.y, position.z, color)
                put(-4f, 0f)
                put(0f, 0f, 0f)
                put(position.x, position.y, position.z, color)
                put(4f, 0f)
                put(0f, 0f, 0f)
                put(position.x, position.y, position.z, color)
                put(0f, -4f)
                put(0f, 0f, 0f)
                put(position.x, position.y, position.z, color)
                put(0f, 4f)
                put(0f, 0f, 0f)
            }
        }
        increaseBatchSize(16 + if (cross) 2 else 0)
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        vbo.destroy()
        vao.destroy()
        program.destroy()
    }
}