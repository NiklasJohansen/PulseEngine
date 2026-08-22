package no.njoh.pulseengine.core.graphics.scene3d.shadow

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContextInternal
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewGroup
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewKey
import no.njoh.pulseengine.core.graphics.scene3d.view.LocalShadowRenderView
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewDeclarer
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawRenderBucket
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import org.lwjgl.opengl.GL11.*

class LocalShadowAtlasRenderer(
    override val order: Int = 0,
    val renderViewGroup: RenderViewGroup? = null
) : Renderer(), RenderViewDeclarer {

    private lateinit var opaqueStaticProgram: ShaderProgram
    private lateinit var opaqueSkinnedProgram: ShaderProgram
    private lateinit var maskedStaticProgram: ShaderProgram
    private lateinit var maskedSkinnedProgram: ShaderProgram
    private lateinit var opaquePrograms: ShaderProgramSet
    private lateinit var maskedPrograms: ShaderProgramSet
    private lateinit var viewKey: RenderViewKey<LocalShadowRenderView>

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        if (!this::opaqueStaticProgram.isInitialized)
        {
            val staticVertex   = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/shadow.vert", ::transformModelVertexShader))
            val skinnedVertex  = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/shadow_skinned.vert", ::transformModelVertexShader))
            val opaqueFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/shadow_opaque.frag"))
            val maskedFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/shadow.frag"))

            opaqueStaticProgram  = ShaderProgram.create(staticVertex, opaqueFragment)
            opaqueSkinnedProgram = ShaderProgram.create(skinnedVertex, opaqueFragment)
            maskedStaticProgram  = ShaderProgram.create(staticVertex, maskedFragment)
            maskedSkinnedProgram = ShaderProgram.create(skinnedVertex, maskedFragment)
            opaquePrograms       = ShaderProgramSet(opaqueStaticProgram, opaqueSkinnedProgram)
            maskedPrograms       = ShaderProgramSet(maskedStaticProgram, maskedSkinnedProgram)
        }

        val atlas = engine.gfx.sceneContext.getLocalShadowAtlas()
        viewKey = RenderViewKey(renderViewGroup ?: surface.viewGroup) { LocalShadowRenderView(atlas) }
    }

    override fun declareRenderViews(engine: PulseEngineInternal, surface: SurfaceInternal, context: SceneRenderContextInternal)
    {
        increaseBatchSize() // Make sure that the batch size is at least 1
        context.requestView(viewKey) // Declares that the view is needed for this frame
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        val atlas = engine.gfx.sceneContext.getLocalShadowAtlas()
        val numFacesToRender = atlas.getNumberOfShadowFacesToRender()
        if (numFacesToRender == 0)
            return

        val view = engine.gfx.sceneContext.getView(viewKey) ?: return

        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glColorMask(false, false, false, false)
        glEnable(GL_POLYGON_OFFSET_FILL)
        glPolygonOffset(SHADOW_SLOPE_BIAS, SHADOW_CONST_BIAS)

        maskedStaticProgram.bind()
        maskedStaticProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        maskedSkinnedProgram.bind()
        maskedSkinnedProgram.setUniformSamplerArrays(engine.gfx.textureBank.getAllTextureArrays())
        
        for (i in 0 until numFacesToRender)
        {
            val shadowFaceIndex = atlas.getShadowFaceIndexToRender(i)
            val shadowFace = atlas.getShadowFace(shadowFaceIndex)
            val pass = view.getShadowFaceRenderPass(shadowFaceIndex) ?: continue

            measure("local_shadow", label = { "Local shadow #" plus shadowFaceIndex plus " (" plus shadowFace.blockKey plus ")" })
            {
                glEnable(GL_SCISSOR_TEST)
                glScissor(shadowFace.x, shadowFace.y, shadowFace.size, shadowFace.size)
                glClear(GL_DEPTH_BUFFER_BIT)
                glDisable(GL_SCISSOR_TEST)
                glViewport(shadowFace.x, shadowFace.y, shadowFace.size, shadowFace.size)

                opaqueStaticProgram.bind()
                opaqueStaticProgram.setUniform("viewProjection", shadowFace.viewProjection)
                opaqueSkinnedProgram.bind()
                opaqueSkinnedProgram.setUniform("viewProjection", shadowFace.viewProjection)
                maskedStaticProgram.bind()
                maskedStaticProgram.setUniform("viewProjection", shadowFace.viewProjection)
                maskedSkinnedProgram.bind()
                maskedSkinnedProgram.setUniform("viewProjection", shadowFace.viewProjection)

                val cullViewIndex = pass.cullViewIndexOf(shadowFaceIndex)
                drawRenderBucket(pass.opaqueBucket, pass.drawPayload, opaquePrograms, cullViewIndex)
                drawRenderBucket(pass.maskedBucket, pass.drawPayload, maskedPrograms, cullViewIndex)
            }
        }

        glViewport(0, 0, surface.config.renderWidth, surface.config.renderHeight)
        glDisable(GL_POLYGON_OFFSET_FILL)
        glColorMask(true, true, true, true)
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        if (this::opaqueStaticProgram.isInitialized) opaqueStaticProgram.destroy()
        if (this::opaqueSkinnedProgram.isInitialized) opaqueSkinnedProgram.destroy()
        if (this::maskedStaticProgram.isInitialized) maskedStaticProgram.destroy()
        if (this::maskedSkinnedProgram.isInitialized) maskedSkinnedProgram.destroy()
    }

    companion object
    {
        private const val SHADOW_SLOPE_BIAS = 3.0f
        private const val SHADOW_CONST_BIAS = 1.0f
    }
}