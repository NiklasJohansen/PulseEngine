package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.VertexAttributeLayout
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawTriangleVertices
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.glDepthMask
import org.lwjgl.opengl.GL11.glDisable

class SkyboxRenderer(
    override val order: Int = 10,
    var envTexture: String  = "",
    var brightness: Float   = 1f
) : Renderer() {

    private lateinit var program: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject

    override fun init(engine: PulseEngineInternal, surface: Surface) 
    {
        if (!this::program.isInitialized) 
        {
            vbo = StaticBufferObject.createArrayBuffer(cubeVertices)
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/skybox.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/skybox.frag"))
            )
        }

        vao = VertexArrayObject.createAndBind()
        vbo.bind()
        VertexAttributeLayout().withAttribute("position", 3, GL_FLOAT).bind()
        vao.release()
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        increaseBatchSize() // To ensure the batch is rendered
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        val tex = engine.asset.getOrNull<Texture>(envTexture) ?: return
        val texArray = engine.gfx.textureBank.getTextureArray(tex) ?: return

        glDepthMask(false) // Disable depth writes skybox
        glDisable(GL_DEPTH_TEST)

        program.bind()
        program.setUniformSamplerArray("textureArray", texArray)
        program.setUniform("texDesc", tex.handle.textureIndex.toFloat(), tex.uMax, tex.vMax)
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", surface.camera.viewMatrix)
        program.setUniform("brightness", brightness)

        drawTriangleVertices(vao, 0, 36) // 12 triangles * 3 verts
    }

    override fun destroy() 
    {
        vao.destroy()
        vbo.destroy()
        program.destroy()
    }

    companion object
    {
        private val cubeVertices = floatArrayOf(
            // Back face
            -1f,  1f, -1f,
            -1f, -1f, -1f,
            1f, -1f, -1f,
            1f, -1f, -1f,
            1f,  1f, -1f,
            -1f,  1f, -1f,

            // Left face
            -1f, -1f,  1f,
            -1f, -1f, -1f,
            -1f,  1f, -1f,
            -1f,  1f, -1f,
            -1f,  1f,  1f,
            -1f, -1f,  1f,

            // Right face
            1f, -1f, -1f,
            1f, -1f,  1f,
            1f,  1f,  1f,
            1f,  1f,  1f,
            1f,  1f, -1f,
            1f, -1f, -1f,

            // Front face
            -1f, -1f,  1f,
            -1f,  1f,  1f,
            1f,  1f,  1f,
            1f,  1f,  1f,
            1f, -1f,  1f,
            -1f, -1f,  1f,

            // Top face
            -1f,  1f, -1f,
            1f,  1f, -1f,
            1f,  1f,  1f,
            1f,  1f,  1f,
            -1f,  1f,  1f,
            -1f,  1f, -1f,

            // Bottom face
            -1f, -1f, -1f,
            -1f, -1f,  1f,
            1f, -1f, -1f,
            1f, -1f, -1f,
            -1f, -1f,  1f,
            1f, -1f,  1f
        )
    }
}
