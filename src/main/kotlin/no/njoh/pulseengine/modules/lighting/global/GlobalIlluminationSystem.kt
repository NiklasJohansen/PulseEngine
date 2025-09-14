package no.njoh.pulseengine.modules.lighting.global

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.BlendFunction.ADDITIVE
import no.njoh.pulseengine.core.graphics.api.BlendFunction.NONE
import no.njoh.pulseengine.core.graphics.api.CustomMipmapGenerator
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.postprocessing.effects.MultiplyEffect
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.modules.scene.systems.EntityRenderer
import no.njoh.pulseengine.modules.scene.systems.EntityRenderer.RenderPass
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.modules.lighting.global.effects.*
import no.njoh.pulseengine.modules.lighting.global.effects.GiJfa.JfaMode.*
import no.njoh.pulseengine.modules.lighting.global.effects.GiRadianceCascades.Companion.BASE_RAY_COUNT
import no.njoh.pulseengine.modules.lighting.shared.NormalMapRenderer
import no.njoh.pulseengine.modules.lighting.shared.NormalMapped
import org.joml.Vector2f

import kotlin.math.*

@Name("Global Illumination")
@Icon("LIGHT_BULB")
open class GlobalIlluminationSystem : SceneSystem()
{
    @Prop(i = 0)                      var ambientLight = Color(0f, 0f, 0f, 1f)
    @Prop(i = 1)                      var ambientInteriorLight = Color(0f, 0f, 0f, 1f)
    @Prop(i = 2)                      var skyLight = true
    @Prop(i = 3)                      var skyColor = Color(0.02f, 0.08f, 0.2f, 1f)
    @Prop(i = 5)                      var sunColor = Color(0.95f, 0.95f, 0.9f, 1f)
    @Prop(i = 6,  min=0f)             var skyIntensity = 0.1f
    @Prop(i = 7,  min=0f)             var sunIntensity = 0.01f
    @Prop(i = 8,  min=0.001f)         var sunDistance = 10f
    @Prop(i = 9,  min=0f, max=360f)   var sunAngle = 0f
    @Prop(i = 10, min=0f)             var dithering = 0.2f
    @Prop(i = 11)                     var lightTexFilter = LINEAR
    @Prop(i = 12, min=0.01f, max=2f)  var lightTexScale = 0.4f
    @Prop(i = 13, min=0.01f, max=4f)  var localSceneTexScale = 0.4f
    @Prop(i = 14, min=0.01f, max=4f)  var globalSceneTexScale = 0.6f
    @Prop(i = 15, min=1f)             var globalWorldScale = 4f
    @Prop(i = 16, min=0f)             var drawCascade = 0
    @Prop(i = 17, min=0f)             var maxCascades = 10
    @Prop(i = 18, min=0f)             var maxSteps = 25
    @Prop(i = 19, min=0f)             var intervalLength = 1.0f
    @Prop(i = 20, min=0f, max=1f)     var bounceAccumulation = 0.7f
    @Prop(i = 21, min=0f)             var bounceRadius = 0f // 0=infinite
    @Prop(i = 22, min=0f, max=1f)     var bounceEdgeFade = 0.2f
    @Prop(i = 23, min=0f)             var normalMapScale = 4f
    @Prop(i = 24, min=0f)             var sourceIntensity = 1f
    @Prop(i = 25, min=0f, max=1f)     var minReflectance = 0.02f
    @Prop(i = 26, min=0f)             var aoRadius = 30f
    @Prop(i = 27, min=0f)             var aoStrength = 1.5f
    @Prop(i = 28)                     var traceWorldRays = true
    @Prop(i = 29)                     var mergeCascades = true
    @Prop(i = 30)                     var bilinearFix = true
    @Prop(i = 31)                     var jitterFix = true
    @Prop(i = 32)                     var upscaleSmaleSources = true
    @Prop(i = 33)                     var targetSurface = "main"

    private var lastTargetSurface = ""

    private val normalMapRenderPass   = RenderPass<NormalMapped>(GI_NORMAL_MAP)    { e, surface -> onRenderNormalMap(e, surface)   }
    private val localOccluderPass     = RenderPass<GiOccluder>(GI_LOCAL_SCENE)     { e, surface -> onRenderOccluder(e, surface)    }
    private val localLightSourcePass  = RenderPass<GiLightSource>(GI_LOCAL_SCENE)  { e, surface -> onRenderLightSource(e, surface) }
    private val globalOccluderPass    = RenderPass<GiOccluder>(GI_GLOBAL_SCENE)    { e, surface -> onRenderOccluder(e, surface)    }
    private val globalLightSourcePass = RenderPass<GiLightSource>(GI_GLOBAL_SCENE) { e, surface -> onRenderLightSource(e, surface) }

    override fun onCreate(engine: PulseEngine)
    {
        engine.gfx.createSurface(
            name = GI_LOCAL_SCENE,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 9,
            isVisible = false,
            backgroundColor = Color.BLANK,
            blendFunction = NONE,
            textureFilter = NEAREST,
            textureScale = localSceneTexScale,
            attachments = listOf(COLOR_TEXTURE_0, COLOR_TEXTURE_1)
        ).apply {
            addRenderer(GiSceneRenderer((this as SurfaceInternal).config))
            addPostProcessingEffect(GiBounce(GI_LIGHT_EXTERIOR, GI_LOCAL_SDF, order = 0))
        }

        engine.gfx.createSurface(
            name = GI_GLOBAL_SCENE,
            zOrder = engine.gfx.mainSurface.config.zOrder + 8,
            isVisible = false,
            backgroundColor = Color.BLANK,
            blendFunction = NONE,
            textureFilter = NEAREST,
            textureScale = globalSceneTexScale,
            attachments = listOf(COLOR_TEXTURE_0, COLOR_TEXTURE_1)
        ).apply {
            addRenderer(GiSceneRenderer((this as SurfaceInternal).config))
        }

        engine.gfx.createSurface(
            name = GI_LOCAL_SDF,
            zOrder = engine.gfx.mainSurface.config.zOrder + 7,
            isVisible = false,
            backgroundColor = Color.BLANK,
            blendFunction = NONE,
            textureScale = localSceneTexScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(GiJfaSeed(mode = EXTERNAL_INTERNAL, sceneSurfaceName = GI_LOCAL_SCENE))
            addPostProcessingEffect(GiJfa(mode = EXTERNAL_INTERNAL))
            addPostProcessingEffect(GiSdf(isSigned = true))
        }

        engine.gfx.createSurface(
            name = GI_GLOBAL_SDF,
            zOrder = engine.gfx.mainSurface.config.zOrder + 6,
            isVisible = false,
            backgroundColor = Color.BLANK,
            blendFunction = NONE,
            textureScale = globalSceneTexScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(GiJfaSeed(mode = EXTERNAL, sceneSurfaceName = GI_GLOBAL_SCENE))
            addPostProcessingEffect(GiJfa(mode = EXTERNAL))
            addPostProcessingEffect(GiSdf(isSigned = false))
        }

        engine.gfx.createSurface(
            name = GI_NORMAL_MAP,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 5,
            isVisible = false,
            backgroundColor = Color(0.5f, 0.5f, 1.0f, 1f),
            textureFilter = LINEAR_MIPMAP,
            mipmapGenerator = CustomMipmapGenerator()
        ).apply {
            addRenderer(NormalMapRenderer((this as SurfaceInternal).config))
        }

        engine.gfx.createSurface(
            name = GI_LIGHT_EXTERIOR,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 4,
            isVisible = false,
            backgroundColor = Color.BLANK,
            attachments = listOf(COLOR_TEXTURE_0),
            textureSizeFunc = ::lightTextureSizeFunc
        ).apply {
            addPostProcessingEffect(GiRadianceCascades(GI_LOCAL_SCENE, GI_GLOBAL_SCENE, GI_LOCAL_SDF, GI_GLOBAL_SDF, GI_NORMAL_MAP))
        }

        engine.gfx.createSurface(
            name = GI_LIGHT_INTERIOR,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 3,
            isVisible = false,
            backgroundColor = Color.BLANK,
            attachments = listOf(COLOR_TEXTURE_0),
        ).apply {
            addPostProcessingEffect(GiInterior(GI_LOCAL_SCENE, GI_LOCAL_SDF, GI_LIGHT_EXTERIOR, GI_NORMAL_MAP))
        }

        engine.gfx.createSurface(
            name = GI_AO,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 2,
            isVisible = false,
            backgroundColor = Color.WHITE,
            blendFunction = NONE,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(GiAo(GI_LOCAL_SCENE, GI_LOCAL_SDF))
        }

        engine.gfx.createSurface(
            name = GI_LIGHT_FINAL,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 1,
            isVisible = false,
            backgroundColor = Color.BLANK,
            blendFunction = ADDITIVE,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(GiFinal(GI_LOCAL_SCENE, GI_LIGHT_EXTERIOR, GI_LIGHT_INTERIOR, GI_AO))
        }

        val entityRenderer = engine.scene.getSystemOfType<EntityRenderer>() ?: return
        entityRenderer.addRenderPass(normalMapRenderPass)
        entityRenderer.addRenderPass(localOccluderPass)
        entityRenderer.addRenderPass(localLightSourcePass)
        entityRenderer.addRenderPass(globalOccluderPass)
        entityRenderer.addRenderPass(globalLightSourcePass)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        engine.gfx.getSurface(GI_LIGHT_EXTERIOR)?.setTextureScale(lightTexScale)
        engine.gfx.getSurface(GI_LIGHT_INTERIOR)?.setTextureScale(localSceneTexScale)
        engine.gfx.getSurface(GI_LOCAL_SDF)?.setTextureScale(localSceneTexScale)
        engine.gfx.getSurface(GI_LOCAL_SCENE)?.setTextureScale(localSceneTexScale)
        engine.gfx.getSurface(GI_GLOBAL_SDF)?.setTextureScale(globalSceneTexScale)
        engine.gfx.getSurface(GI_GLOBAL_SCENE)?.setTextureScale(globalSceneTexScale)
        engine.gfx.getSurface(GI_AO)?.setTextureScale(localSceneTexScale)

        engine.gfx.getSurface(GI_LOCAL_SCENE)?.getRenderer<GiSceneRenderer>()?.jitterFix = jitterFix
        engine.gfx.getSurface(GI_GLOBAL_SCENE)?.getRenderer<GiSceneRenderer>()?.let()
        {
            it.jitterFix = jitterFix
            it.globalWorldScale = globalWorldScale
            it.upscaleSmallSources = upscaleSmaleSources
        }

        if (targetSurface != lastTargetSurface)
        {
            engine.gfx.getSurface(lastTargetSurface)?.deletePostProcessingEffect(GI_BLEND_EFFECT)
            engine.gfx.getSurface(targetSurface)?.addPostProcessingEffect(MultiplyEffect(GI_BLEND_EFFECT, order = 15, GI_LIGHT_FINAL, minReflectance))
            lastTargetSurface = targetSurface
        }

        engine.gfx.getSurface(targetSurface)?.getPostProcessingEffect<MultiplyEffect>()?.minReflectance = minReflectance
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (traceWorldRays)
        {
            val globalSurface = engine.gfx.getSurface(GI_GLOBAL_SCENE) ?: return
            val cam = globalSurface.camera
            val scale = 1f / max(1f, globalWorldScale)
            cam.position.set(engine.gfx.mainCamera.position)
            cam.rotation.set(engine.gfx.mainCamera.rotation)
            cam.scale.set(engine.gfx.mainCamera.scale.x * scale, engine.gfx.mainCamera.scale.y * scale, engine.gfx.mainCamera.scale.z)
            cam.origin.set(0.5f * globalSurface.config.width, 0.5f * globalSurface.config.height, 0f)
        }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        val entityRenderer = engine.scene.getSystemOfType<EntityRenderer>()
        entityRenderer?.removeRenderPass(normalMapRenderPass)
        entityRenderer?.removeRenderPass(localOccluderPass)
        entityRenderer?.removeRenderPass(localLightSourcePass)
        entityRenderer?.removeRenderPass(globalOccluderPass)
        entityRenderer?.removeRenderPass(globalLightSourcePass)

        engine.gfx.getSurface(targetSurface)?.deletePostProcessingEffect(GI_BLEND_EFFECT)
        engine.gfx.deleteSurface(GI_LOCAL_SCENE)
        engine.gfx.deleteSurface(GI_GLOBAL_SCENE)
        engine.gfx.deleteSurface(GI_LOCAL_SDF)
        engine.gfx.deleteSurface(GI_GLOBAL_SDF)
        engine.gfx.deleteSurface(GI_NORMAL_MAP)
        engine.gfx.deleteSurface(GI_LIGHT_EXTERIOR)
        engine.gfx.deleteSurface(GI_LIGHT_INTERIOR)
        engine.gfx.deleteSurface(GI_AO)
        engine.gfx.deleteSurface(GI_LIGHT_FINAL)

        lastTargetSurface = ""
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }

    private fun lightTextureSizeFunc(width: Int, height: Int, scale: Float): PackedSize
    {
        val scaledWidth = ceil(width * scale).toInt()
        val scaledHeight = ceil(height * scale).toInt()
        val diagonal = sqrt((scaledWidth * scaledWidth + scaledHeight * scaledHeight).toFloat())
        val cascadeCount = min(ceil(log2(diagonal) / log2(BASE_RAY_COUNT)).toInt() + 1, max(1, maxCascades))
        val maxProbeSize = 2f.pow(cascadeCount)
        val w = ceil(scaledWidth / maxProbeSize) * maxProbeSize
        val h = ceil(scaledHeight / maxProbeSize) * maxProbeSize
        return PackedSize(w, h)
    }

    fun getExteriorLightTexUvMax(engine: PulseEngine): Vector2f
    {
        val lightSurface = engine.gfx.getSurface(GI_LIGHT_EXTERIOR) ?: return UV_MAX.set(1f, 1f)
        val lightTex = lightSurface.getTexture()
        val scaledLightTexWidth = lightSurface.config.width * lightSurface.config.textureScale
        val scaledLightTexHeight = lightSurface.config.height * lightSurface.config.textureScale
        val uMax = (scaledLightTexWidth / lightTex.width)
        val vMax = (scaledLightTexHeight / lightTex.height)
        return UV_MAX.set(uMax, vMax)
    }

    companion object
    {
        private val UV_MAX = Vector2f(1f, 1f)

        const val GI_LOCAL_SCENE    = "gi_local_scene"
        const val GI_GLOBAL_SCENE   = "gi_global_scene"
        const val GI_LOCAL_SDF      = "gi_local_sdf"
        const val GI_GLOBAL_SDF     = "gi_global_sdf"
        const val GI_NORMAL_MAP     = "gi_normal_map"
        const val GI_LIGHT_EXTERIOR = "gi_light_exterior"
        const val GI_LIGHT_INTERIOR = "gi_light_interior"
        const val GI_AO             = "gi_ao"
        const val GI_LIGHT_FINAL    = "gi_light_final"
        const val GI_BLEND_EFFECT   = "gi_blend_effect"
    }
}