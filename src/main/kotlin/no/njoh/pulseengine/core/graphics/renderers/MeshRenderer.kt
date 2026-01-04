package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Mesh
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.surface.Surface
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*

class MeshRenderer : Renderer()
{
    private var readDrawCommands = ArrayList<DrawCommand>(256)
    private var writeDrawCommands = ArrayList<DrawCommand>(256)
    private val invViewMatrix = Matrix4f()
    private val camPos = Vector3f()

    private lateinit var program: ShaderProgram

    var envDiffuseTexture = ""
    var envSpecularTexture = ""
    var brdfLutTexture = ""

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::program.isInitialized)
        {
            program = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/mesh.vert")),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/mesh.frag"))
            )
        }
    }

    override fun onInitFrame()
    {
        writeDrawCommands = readDrawCommands.also { readDrawCommands = writeDrawCommands }
        writeDrawCommands.clear()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex > 0) return // Only once per frame

        // Camera position
        surface.camera.viewMatrix.invert(invViewMatrix)
        invViewMatrix.getTranslation(camPos)

        val envSpecularMipCount = engine.asset.getOrNull<Texture>(envSpecularTexture)
            ?.let { engine.gfx.textureBank.getTextureArray(it) }?.mipLevels?.toFloat() ?: 1f

        program.bind()
        program.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        program.setUniform("viewProjection", surface.camera.viewProjectionMatrix)
        program.setUniform("cameraPos", camPos)
        program.setUniform("envSpecularMipCount", envSpecularMipCount)

        program.setTexture("envDiffuseTex",  engine.asset.getOrNull(envDiffuseTexture))
        program.setTexture("envSpecularTex", engine.asset.getOrNull(envSpecularTexture))
        program.setTexture("brdfLutTex",     engine.asset.getOrNull(brdfLutTexture))

        for (i in startIndex until startIndex + drawCount)
        {
            val cmd = readDrawCommands.getOrNull(i) ?: continue
            val vao = cmd.mesh.vao ?: continue
            
            for (subMesh in cmd.mesh.subMeshes) 
            {
                val matInfo = cmd.mesh.materials.getOrNull(subMesh.materialIndex)
                val material = matInfo?.name?.let { engine.asset.getOrNull<Material>(it) }

                program.setTexture("albedoTex", material?.albedo)
                program.setTexture("normalTex", material?.normal)
                program.setTexture("aoMetalRoughTex", material?.aoMetalRough)
                program.setTexture("specularTex", material?.specular)
                program.setTexture("emissiveTex", material?.emissive)
                program.setUniform("model", cmd.transform)

                vao.bind()
                glDrawElements(GL_TRIANGLES, subMesh.indexCount, GL_UNSIGNED_INT, (subMesh.indexStart.toLong() * 4L)) // 4 bytes per uint index
                vao.release()
            }
        }
    }

    private fun ShaderProgram.setTexture(name: String, tex: Texture?)
    {
        if (tex != null)
            setUniform(name, tex.handle.samplerIndex.toFloat(), tex.handle.textureIndex.toFloat(), tex.uMax, tex.vMax)
        else 
            setUniform(name, -1f, 0f, 0f, 0f)
    }
    
    override fun destroy() {}
    
    fun draw(mesh: Mesh, transform: Matrix4f) 
    {
        writeDrawCommands += DrawCommand(mesh, transform)
        increaseBatchSize()
    }

    private data class DrawCommand(
        val mesh: Mesh,
        val transform: Matrix4f
    )
}