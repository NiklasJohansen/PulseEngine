package no.njoh.pulseengine.core.graphics.scene3d.renderers

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderState
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderView
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureCompare
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.CLAMP_TO_BORDER
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.CLAMP_TO_EDGE
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import no.njoh.pulseengine.core.graphics.scene3d.draw.TransparencyMode.SORTED_BLEND
import no.njoh.pulseengine.core.graphics.scene3d.draw.TransparencyMode.WEIGHTED_BLENDED_OIT
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContextInternal
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.scene3d.lighting.BrdfLutBuilder
import no.njoh.pulseengine.core.graphics.scene3d.shadow.CascadedShadowMapRenderer
import no.njoh.pulseengine.core.graphics.util.DrawUtils.drawRenderBucket
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewIds.MAIN_CAMERA
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewKey
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewDeclarer
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.graphics.util.transformModelVertexShader
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Color.Companion.WHITE
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_ONE
import org.lwjgl.opengl.GL14.glBlendFuncSeparate

class ModelRenderer(
    override val order: Int = 40,
    val cameraViewId: Int = MAIN_CAMERA
) : Renderer(), RenderViewDeclarer {

    var iblDiffuseTexture  = ""
    var iblSpecularTexture = ""
    var iblIntensity       = 1f
    var iblBrdfTexture     = "ibl_brdf_lut"

    var sunColor                    = Color(1f, 1f, 1f)
    var sunRadius                   = 1f
    var sunShadowMapSurfaceName     = ""
    var localShadowAtlasSurfaceName = ""

    var transparencyMode         = WEIGHTED_BLENDED_OIT
    var weightedBlendAlphaCutoff = 0.04f

    private lateinit var staticProgram: ShaderProgram
    private lateinit var skinnedProgram: ShaderProgram
    private lateinit var programs: ShaderProgramSet

    private val weightedBlendedRenderer = WeightedBlendedOitRenderer()
    private val viewKey = RenderViewKey(cameraViewId) { CameraRenderView(cameraViewId) }

    override fun init(engine: PulseEngineInternal, surface: Surface)
    {
        if (!this::staticProgram.isInitialized)
        {
            val staticVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr.vert", ::transformModelVertexShader))
            val skinnedVertex = engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/model_pbr_skinned.vert", ::transformModelVertexShader))
            val pbrFragment = engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_pbr.frag"))

            staticProgram = ShaderProgram.create(staticVertex, pbrFragment)
            skinnedProgram = ShaderProgram.create(skinnedVertex, pbrFragment)
            programs = ShaderProgramSet(staticProgram, skinnedProgram)
        }

        weightedBlendedRenderer.init(engine, surface)

        if (engine.asset.getOrNull<Texture>(iblBrdfTexture) == null)
        {
            val brdfLutTex = Texture(
                name = iblBrdfTexture,
                filePath = "",
                initWidth = 512,
                initHeight = 512,
                format = TextureFormat.RGBA16F,
                wrapping = CLAMP_TO_EDGE,
                filter = LINEAR,
                maxMipLevels = 1
            )
            engine.asset.loadNow(brdfLutTex)
            BrdfLutBuilder.generate(engine, brdfLutTex)
        }
    }

    override fun declareRenderViews(engine: PulseEngineInternal, surface: SurfaceInternal, context: SceneRenderContextInternal)
    {
        increaseBatchSize() // Ensure that the batch size is at least 1

        val view = context.requestView(viewKey)
        val cameraState = view.addCameraStateFor(surface.camera, surface.config.width, surface.config.height)
        context.requestClusteredLightGrid(cameraState)
        view.transparencyMode = transparencyMode
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return // Only once per frame

        val view = engine.gfx.sceneContext.getView(viewKey) ?: return
        val cameraState = view.getCameraState(surface.camera) ?: return

        glEnable(GL_DEPTH_TEST)
        glDisable(GL_BLEND)

        val hasDepthPrepass = surface.config.hasDepthPrepass
        glDepthFunc(if (hasDepthPrepass) GL_LEQUAL else GL_LESS)
        glDepthMask(!hasDepthPrepass)

        configureProgram(staticProgram, engine, surface, cameraState)
        configureProgram(skinnedProgram, engine, surface, cameraState)
        render(engine, surface, view, cameraState)

        glDepthMask(true)
    }

    private fun render(engine: PulseEngineInternal, surface: SurfaceInternal, view: CameraRenderView, cameraState: CameraRenderState)
    {
        measure({"opaque (" plus view.opaqueBucket.instanceCount plus "i, " plus view.opaqueBucket.size plus "b)"})
        {
            drawRenderBucket(view.opaqueBucket, view.drawPayload, programs)
        }

        val maskedCount = view.maskedBucket.instanceCount
        if (maskedCount > 0)
        {
            measure({"masked (" plus maskedCount plus "i, " plus view.maskedBucket.size plus "b)"})
            {
                glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE)
                glEnable(GL_SAMPLE_ALPHA_TO_ONE)
                glDepthFunc(GL_LEQUAL)
                glDepthMask(true)

                drawRenderBucket(view.maskedBucket, view.drawPayload, programs)

                glDisable(GL_SAMPLE_ALPHA_TO_ONE)
                glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE)
            }
        }

        val blendedCount = view.blendedBucket.instanceCount
        if (blendedCount > 0)
        {
            when (transparencyMode)
            {
                SORTED_BLEND -> measure({"blended (" plus blendedCount plus "i, " plus view.blendedBucket.size plus "b)"})
                {
                    glEnable(GL_BLEND)
                    glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
                    glDepthFunc(GL_LEQUAL)
                    glDepthMask(false)

                    drawRenderBucket(view.blendedBucket, view.drawPayload, programs)
                }
                WEIGHTED_BLENDED_OIT ->
                {
                    weightedBlendedRenderer.alphaCutoff = weightedBlendAlphaCutoff
                    weightedBlendedRenderer.render(
                        engine = engine,
                        surface = surface,
                        bucket = view.blendedBucket,
                        drawPayload = view.drawPayload,
                        configureAccumProgram = { program -> configureProgram(program, engine, surface, cameraState) }
                    )
                }
            }
        }
    }

    private fun configureProgram(program: ShaderProgram, engine: PulseEngineInternal, surface: Surface, cameraState: CameraRenderState)
    {
        // Textures

        val texBank = engine.gfx.textureBank
        program.bind()
        program.setUniformSamplerArrays(texBank.getAllTextureArrays())

        // Ambient occlusion

        val aoRenderer = surface.getRenderer<GtaoRenderer>()
        val aoTex = aoRenderer?.getAoRenderTexture() ?: texBank.getOrCreateFallbackTexture(WHITE)
        program.setUniformSampler("uGtaoTex", aoTex)
        program.setUniform("uAoIntensity", aoRenderer?.intensity ?: 0f)

        // Cascaded shadow mapping

        val shadowMapSurface = engine.gfx.getSurface(sunShadowMapSurfaceName)
        val shadowMapRenderer = shadowMapSurface?.getRenderer<CascadedShadowMapRenderer>()
        val shadowMapTex = shadowMapSurface?.getTexture() ?: texBank.getOrCreateFallbackTexture(WHITE)
        val sunViewProjections = shadowMapRenderer?.getViewProjectionMatrices() ?: fallbackShadowVPs
        val splitDist = shadowMapRenderer?.getCascadeSplitDistances() ?: fallbackSplitDists
        val cascadeSize = shadowMapRenderer?.getCascadeSizeMeters() ?: fallbackCascadeSizes
        program.setUniformSampler("uShadowMapTex", shadowMapTex, filter = LINEAR, wrapping = CLAMP_TO_BORDER, compare = TextureCompare.LEQUAL, borderColor = WHITE)
        program.setUniform("uShadowMapTexSize", shadowMapRenderer?.resolution?.toFloat() ?: 1024f)
        program.setUniform("uShadowViewProjections", sunViewProjections)
        program.setUniform("uShadowCascadeSplitDistances", splitDist[0], splitDist[1], splitDist[2], splitDist[3])
        program.setUniform("uShadowCascadeSizeMeters", cascadeSize[0], cascadeSize[1], cascadeSize[2], cascadeSize[3])

        // Sunlight

        program.setUniform("uSunColor", sunColor)
        program.setUniform("uSunDirection", shadowMapRenderer?.getDirection() ?: fallbackSunDirection)
        program.setUniform("uSunRadius", sunRadius)
        
        // Local shadow atlas

        val lightBuffer = engine.gfx.sceneContext.getLightBuffer().also { it.bind() }
        val localShadowAtlasSurface = engine.gfx.getSurface(localShadowAtlasSurfaceName)
        val localShadowAtlasTex = localShadowAtlasSurface?.getTexture() ?: texBank.getOrCreateFallbackTexture(WHITE)
        val localShadowAtlas = engine.gfx.sceneContext.getLocalShadowAtlas()
        program.setUniformSampler("uLocalShadowAtlasTex", localShadowAtlasTex, filter = LINEAR, wrapping = CLAMP_TO_BORDER, compare = TextureCompare.LEQUAL, borderColor = WHITE)
        program.setUniform("uLocalShadowAtlasTexSize", localShadowAtlas.resolution.toFloat())
        program.setUniform("uLocalShadowFaceCount", lightBuffer.shadowFaceCount)

        // Local clustered lights

        val grid = engine.gfx.sceneContext.getClusteredLightGrid(cameraState)
        if (grid?.enabled == true) grid.bind()
        program.setUniform("uClusteredLightingEnabled", grid?.enabled ?: false)
        program.setUniform("uClusterGridSize", grid?.gridWidth ?: 1, grid?.gridHeight ?: 1, grid?.gridDepth ?: 24)
        program.setUniform("uClusterTileSize", grid?.xTileSize?.toFloat() ?: 64f, grid?.yTileSize?.toFloat() ?: 64f)
        program.setUniform("uClusterNearPlane", grid?.nearPlane ?: 0.05f)
        program.setUniform("uClusterDepthSliceScale", grid?.depthSliceScale ?: 1f)
        program.setUniform("uClusterCount", grid?.clusterCount ?: 1)
        program.setUniform("uClusterLightCount", lightBuffer.lightCount)

        // Ambient lighting

        val envSpecularMipCount = engine.asset.getOrNull<Texture>(iblSpecularTexture)?.let { texBank.getTextureArray(it) }?.mipLevels?.toFloat() ?: 1f
        program.setUniform("uEnvSpecularMipCount", envSpecularMipCount)
        program.setUniform("uEnvIntensity", iblIntensity)
        program.setTexture("uEnvDiffuseTex", engine.asset.getOrNull(iblDiffuseTexture))
        program.setTexture("uEnvSpecularTex", engine.asset.getOrNull(iblSpecularTexture))
        program.setTexture("uEnvBrdfLutTex", engine.asset.getOrNull(iblBrdfTexture))

        // Camera

        program.setUniform("uScreenSize", cameraState.screenWidth.toFloat(), cameraState.screenHeight.toFloat())
        program.setUniform("uViewProjection", cameraState.viewProjectionMatrix)
        program.setUniform("uView", cameraState.viewMatrix)
        program.setUniform("uCameraPos", cameraState.cameraPosition)
    }

    private fun ShaderProgram.setTexture(name: String, tex: Texture?)
    {
        if (tex != null)
            setUniform(name, tex.handle.samplerIndex.toFloat(), tex.handle.textureIndex.toFloat(), tex.uMax, tex.vMax)
        else
            setUniform(name, -1f, 0f, 0f, 0f)
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        staticProgram.destroy()
        skinnedProgram.destroy()
        weightedBlendedRenderer.destroy()
    }

    companion object
    {
        private val fallbackShadowVPs    = Array(CascadedShadowMapRenderer.CASCADE_COUNT) { Matrix4f() }
        private val fallbackSplitDists   = FloatArray(CascadedShadowMapRenderer.CASCADE_COUNT)
        private val fallbackCascadeSizes = FloatArray(CascadedShadowMapRenderer.CASCADE_COUNT) { 15f }
        private val fallbackSunDirection = Vector3f(0f, 1f, 0f)
    }
}