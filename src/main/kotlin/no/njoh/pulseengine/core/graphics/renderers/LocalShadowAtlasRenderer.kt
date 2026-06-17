package no.njoh.pulseengine.core.graphics.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderContextInternal
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderViewKey
import no.njoh.pulseengine.core.graphics.api.world.views.RenderViewIds.LOCAL_SHADOW
import no.njoh.pulseengine.core.graphics.api.world.views.WorldLocalShadowRenderView
import no.njoh.pulseengine.core.graphics.api.world.views.WorldViewDeclarer
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawWorldRenderBucket
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import org.lwjgl.opengl.GL11.*

class LocalShadowAtlasRenderer(
    override val order: Int = 0,
    val viewId: Int = LOCAL_SHADOW
) : Renderer(), WorldViewDeclarer {

    private lateinit var staticProgram: ShaderProgram
    private lateinit var skinnedProgram: ShaderProgram
    private lateinit var programs: ShaderProgramSet
    private lateinit var viewKey: WorldRenderViewKey<WorldLocalShadowRenderView>

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

        viewKey = WorldRenderViewKey(viewId) { WorldLocalShadowRenderView(viewId, engine.gfx.worldContext.getLocalShadowAtlas()) }
    }

    override fun declareWorldViews(engine: PulseEngineInternal, surface: SurfaceInternal, context: WorldRenderContextInternal)
    {
        increaseBatchSize() // Make sure that the batch size is at least 1
        context.requestView(viewKey) // Declares that the view is needed for this frame
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        val atlas = engine.gfx.worldContext.getLocalShadowAtlas()
        val numFacesToRender = atlas.getNumberOfShadowFacesToRender()
        if (numFacesToRender == 0)
            return

        val view = engine.gfx.worldContext.getView(viewKey) ?: return

        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glColorMask(false, false, false, false)
        glEnable(GL_POLYGON_OFFSET_FILL)
        glPolygonOffset(SHADOW_SLOPE_BIAS, SHADOW_CONST_BIAS)

        staticProgram.bind()
        staticProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        skinnedProgram.bind()
        skinnedProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        
        for (i in 0 until numFacesToRender)
        {
            val shadowFaceIndex = atlas.getShadowFaceIndexToRender(i)
            val shadowFace = atlas.getShadowFace(shadowFaceIndex)
            val pass = view.getShadowFaceRenderPass(shadowFaceIndex) ?: continue

            measure({ "local shadow #" plus shadowFaceIndex plus " (" plus shadowFace.blockKey plus ")" })
            {
                glEnable(GL_SCISSOR_TEST)
                glScissor(shadowFace.x, shadowFace.y, shadowFace.size, shadowFace.size)
                glClear(GL_DEPTH_BUFFER_BIT)
                glDisable(GL_SCISSOR_TEST)
                glViewport(shadowFace.x, shadowFace.y, shadowFace.size, shadowFace.size)

                staticProgram.bind()
                staticProgram.setUniform("viewProjection", shadowFace.viewProjection)
                skinnedProgram.bind()
                skinnedProgram.setUniform("viewProjection", shadowFace.viewProjection)

                drawWorldRenderBucket(pass.bucket, pass.preparedPass, programs, pass.cullViewIndexOf(shadowFaceIndex))
            }
        }

        glViewport(0, 0, surface.config.width, surface.config.height)
        glDisable(GL_POLYGON_OFFSET_FILL)
        glColorMask(true, true, true, true)
    }

    override fun destroy(engine: PulseEngineInternal)
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