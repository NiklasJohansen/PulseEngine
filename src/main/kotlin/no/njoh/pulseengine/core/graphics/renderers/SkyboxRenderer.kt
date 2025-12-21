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
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_LEQUAL
import org.lwjgl.opengl.GL11.GL_LESS
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.glDepthFunc
import org.lwjgl.opengl.GL11.glDepthMask
import org.lwjgl.opengl.GL11.glDrawArrays

class SkyboxRenderer() : BatchRenderer() 
{
    private lateinit var program: ShaderProgram
    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: StaticBufferObject

    var envTextureName = ""

    override fun init(engine: PulseEngineInternal) 
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

    override fun onInitFrame()
    {
        increaseBatchSize() // To ensure the batch is rendered
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: Surface, startIndex: Int, drawCount: Int) 
    {
        if (startIndex != 0) return
        
        val tex = engine.asset.getOrNull<Texture>(envTextureName) ?: return
        val texArray = engine.gfx.textureBank.getTextureArray(tex) ?: return

        // Setup depth state
        glDepthFunc(GL_LEQUAL)
        glDepthMask(false)

        program.bind()
        program.setUniformSamplerArray("textureArray", texArray)
        program.setUniform("texDesc", tex.handle.textureIndex.toFloat(), tex.uMax, tex.vMax)
        program.setUniform("projection", surface.camera.projectionMatrix)
        program.setUniform("view", surface.camera.viewMatrix)

        vao.bind()
        glDrawArrays(GL_TRIANGLES, 0, 36) // 12 triangles * 3 verts
        vao.release()

        glDepthMask(true)
        glDepthFunc(GL_LESS)
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
