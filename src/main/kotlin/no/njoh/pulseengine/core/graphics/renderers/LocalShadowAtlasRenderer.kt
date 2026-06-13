package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.api.world.views.ViewIds.LOCAL_SHADOW_VIEW
import no.njoh.pulseengine.core.graphics.api.world.views.WorldLocalShadowRenderView
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawWorldRenderBucket
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import org.lwjgl.opengl.GL11.*

class LocalShadowAtlasRenderer(
    override val order: Int = 0,
    val viewId: Int = LOCAL_SHADOW_VIEW
) : Renderer() {

    private lateinit var staticProgram: ShaderProgram
    private lateinit var skinnedProgram: ShaderProgram
    private lateinit var programs: ShaderProgramSet

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::staticProgram.isInitialized)
        {
            staticProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/shadow.vert", ::transformModelVertexShader)),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/shadow.frag"))
            )
            skinnedProgram = ShaderProgram.create(
                engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/shadow_skinned.vert", ::transformModelVertexShader)),
                engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/shadow.frag"))
            )
            programs = ShaderProgramSet(staticProgram, skinnedProgram)
        }

        if (engine.gfx.worldContext.getView<WorldLocalShadowRenderView>(viewId) == null)
            engine.gfx.worldContext.addView(WorldLocalShadowRenderView(viewId, engine.gfx.worldContext.getLocalShadowAtlas()))
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        increaseBatchSize()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        val atlas = engine.gfx.worldContext.getLocalShadowAtlas()
        val updateFaceCount = atlas.getUpdateFaceCount()
        if (updateFaceCount == 0)
            return

        val view = engine.gfx.worldContext.getView<WorldLocalShadowRenderView>(viewId) ?: return

        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glColorMask(false, false, false, false)
        glEnable(GL_POLYGON_OFFSET_FILL)
        glPolygonOffset(SHADOW_SLOPE_BIAS, SHADOW_CONST_BIAS)

        staticProgram.bind()
        staticProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        skinnedProgram.bind()
        skinnedProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        
        for (i in 0 until updateFaceCount)
        {
            val faceIndex = atlas.getUpdateFaceIndex(i)
            val face = atlas.getFace(faceIndex)
            val facePass = view.getFacePass(faceIndex) ?: continue

            measure({ "local shadow #" plus faceIndex plus " (" plus face.blockKey plus ")" })
            {
                glEnable(GL_SCISSOR_TEST)
                glScissor(face.x, face.y, face.size, face.size)
                glClear(GL_DEPTH_BUFFER_BIT)
                glDisable(GL_SCISSOR_TEST)
                glViewport(face.x, face.y, face.size, face.size)

                staticProgram.bind()
                staticProgram.setUniform("viewProjection", face.viewProjection)
                skinnedProgram.bind()
                skinnedProgram.setUniform("viewProjection", face.viewProjection)

                drawWorldRenderBucket(facePass.bucket, facePass.preparedPass, programs, facePass.cullViewIndexOf(faceIndex))
            }
        }

        glViewport(0, 0, surface.config.width, surface.config.height)
        glDisable(GL_POLYGON_OFFSET_FILL)
        glColorMask(true, true, true, true)
    }

    override fun destroy()
    {
        if (this::staticProgram.isInitialized) staticProgram.destroy()
        if (this::skinnedProgram.isInitialized) skinnedProgram.destroy()
    }

    companion object
    {
        private const val SHADOW_SLOPE_BIAS = 3.0f
        private const val SHADOW_CONST_BIAS = 1.0f
    }
}