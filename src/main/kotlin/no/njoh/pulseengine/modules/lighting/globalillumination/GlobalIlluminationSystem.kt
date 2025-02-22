package no.njoh.pulseengine.modules.lighting.globalillumination

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.BlendFunction.NONE
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.postprocessing.effects.MultiplyEffect
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.modules.lighting.globalillumination.effects.*

import kotlin.math.*

@Name("Global Illumination")
@Icon("LIGHT_BULB")
open class GlobalIlluminationSystem : SceneSystem()
{
    @Prop(i = 0)                      var skyLight = true
    @Prop(i = 1)                      var skyColor = Color(0.02f, 0.08f, 0.2f, 1f)
    @Prop(i = 2)                      var sunColor = Color(0.95f, 0.95f, 0.9f, 1f)
    @Prop(i = 3)                      var skyIntensity = 0.1f
    @Prop(i = 5)                      var sunIntensity = 0.01f
    @Prop(i = 6)                      var sunDistance = 10f
    @Prop(i = 7)                      var sunAngle = 0f
    @Prop(i = 8)                      var dithering = 0.7f
    @Prop(i = 9)                      var textureFilter = LINEAR
    @Prop(i = 10, min=0.01f, max=2f)  var lightTextureScale = 0.5f
    @Prop(i = 11, min=0.01f, max=2f)  var localSceneTextureScale = 0.4f
    @Prop(i = 12, min=0.01f, max=4f)  var globalSceneTextureScale = 0.8f
    @Prop(i = 13, min=0f)             var drawCascade = 0
    @Prop(i = 14, min=0f)             var maxCascades = 10
    @Prop(i = 15, min=0f)             var maxSteps = 30
    @Prop(i = 16, min=0f)             var intervalLength = 1.5f
    @Prop(i = 17, min=0f, max=1f)     var intervalOverlap = 1f
    @Prop(i = 19, min=0f, max=1f)     var bounceAccumulation = 0.5f
    @Prop(i = 20, min=0f)             var bounceRadius = 0f // 0=infinite
    @Prop(i = 21, min=0f, max=1f)     var bounceEdgeFade = 0.2f
    @Prop(i = 22, min=0f)             var sourceMultiplier = 1f
    @Prop(i = 23)                     var occluderAmbientLight = Color(0f, 0f, 0f, 1f)
    @Prop(i = 24, min=1f)             var worldScale = 4f
    @Prop(i = 25)                     var traceWorldRays = true
    @Prop(i = 26)                     var mergeCascades = true
    @Prop(i = 27)                     var bilinearFix = true
    @Prop(i = 28)                     var forkFix = true
    @Prop(i = 29)                     var fixJitter = true
    @Prop(i = 30)                     var targetSurface = "main"

    private var lastTargetSurface = ""

    override fun onCreate(engine: PulseEngine)
    {
        engine.gfx.createSurface(
            name = GI_LOCAL_SCENE,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 5,
            isVisible = false,
            backgroundColor = Color.BLANK,
            blendFunction = NONE,
            textureFormat = RGBA16F,
            textureFilter = NEAREST,
            textureScale = localSceneTextureScale,
            attachments = listOf(COLOR_TEXTURE_0, COLOR_TEXTURE_1)
        ).apply {
            addRenderer(GiSceneRenderer((this as SurfaceInternal).config))
            addPostProcessingEffect(GiSceneBounce(GI_LIGHT_RAW))
        }

        engine.gfx.createSurface(
            name = GI_GLOBAL_SCENE,
            zOrder = engine.gfx.mainSurface.config.zOrder + 4,
            isVisible = false,
            backgroundColor = Color.BLANK,
            blendFunction = NONE,
            textureFormat = RGBA16F,
            textureFilter = NEAREST,
            textureScale = globalSceneTextureScale,
            attachments = listOf(COLOR_TEXTURE_0, COLOR_TEXTURE_1)
        ).apply {
            addRenderer(GiSceneRenderer((this as SurfaceInternal).config))
        }

        engine.gfx.createSurface(
            name = GI_LOCAL_SDF,
            zOrder = engine.gfx.mainSurface.config.zOrder + 3,
            isVisible = false,
            backgroundColor = Color.BLANK,
            blendFunction = NONE,
            textureScale = localSceneTextureScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(GiJfaSeed(GI_LOCAL_SCENE))
            addPostProcessingEffect(GiJfa())
            addPostProcessingEffect(GiSdf())
        }

        engine.gfx.createSurface(
            name = GI_GLOBAL_SDF,
            zOrder = engine.gfx.mainSurface.config.zOrder + 3,
            isVisible = false,
            backgroundColor = Color.BLANK,
            blendFunction = NONE,
            textureScale = globalSceneTextureScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(GiJfaSeed(GI_GLOBAL_SCENE))
            addPostProcessingEffect(GiJfa())
            addPostProcessingEffect(GiSdf())
        }

        engine.gfx.createSurface(
            name = GI_LIGHT_RAW,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 2,
            isVisible = false,
            backgroundColor = Color.BLANK,
            textureScale = lightTextureScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(GiRadianceCascades(GI_LOCAL_SCENE, GI_GLOBAL_SCENE, GI_LOCAL_SDF, GI_GLOBAL_SDF))
        }

        engine.gfx.createSurface(
            name = GI_LIGHT_FINAL,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 1,
            isVisible = false,
            backgroundColor = Color.BLANK,
            textureScale = 1f,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(GiCompose(GI_LOCAL_SCENE, GI_LOCAL_SDF, GI_LIGHT_RAW))
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        engine.gfx.getSurface(GI_LOCAL_SCENE)?.setTextureScale(localSceneTextureScale)
        engine.gfx.getSurface(GI_LOCAL_SCENE)?.getRenderer<GiSceneRenderer>()?.fixJitter = fixJitter
        engine.gfx.getSurface(GI_GLOBAL_SCENE)?.setTextureScale(globalSceneTextureScale)
        engine.gfx.getSurface(GI_GLOBAL_SCENE)?.getRenderer<GiSceneRenderer>()?.fixJitter = fixJitter
        engine.gfx.getSurface(GI_LIGHT_RAW)?.setTextureScale(lightTextureScale)
        engine.gfx.getSurface(GI_LOCAL_SDF)?.setTextureScale(localSceneTextureScale)
        engine.gfx.getSurface(GI_GLOBAL_SDF)?.setTextureScale(globalSceneTextureScale)
        setTargetSurface(engine)
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (traceWorldRays)
        {
            val globalSurface = engine.gfx.getSurface(GI_GLOBAL_SCENE) ?: return
            val cam = globalSurface.camera
            val scale = 1f / max(1f, worldScale)
            cam.position.set(engine.gfx.mainCamera.position)
            cam.rotation.set(engine.gfx.mainCamera.rotation)
            cam.origin.set(engine.gfx.mainCamera.origin)
            cam.scale.set(engine.gfx.mainCamera.scale.x * scale, engine.gfx.mainCamera.scale.y * scale, engine.gfx.mainCamera.scale.z)
        }
    }

    override fun onRender(engine: PulseEngine)
    {
        val localSceneSurface = engine.gfx.getSurface(GI_LOCAL_SCENE) ?: return
        drawScene(engine, localSceneSurface)

        val globalSceneSurface = engine.gfx.getSurface(GI_GLOBAL_SCENE) ?: return
        if (traceWorldRays)
            drawScene(engine, globalSceneSurface)
    }

    override fun onDestroy(engine: PulseEngine)
    {
        engine.gfx.mainSurface.deletePostProcessingEffect(GI_BLEND_EFFECT)
        engine.gfx.deleteSurface(GI_LOCAL_SCENE)
        engine.gfx.deleteSurface(GI_GLOBAL_SCENE)
        engine.gfx.deleteSurface(GI_LIGHT_RAW)
        engine.gfx.deleteSurface(GI_LIGHT_FINAL)
        engine.gfx.deleteSurface(GI_LOCAL_SDF)
        engine.gfx.deleteSurface(GI_GLOBAL_SDF)
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }

    private fun drawScene(engine: PulseEngine, surface: Surface)
    {
        engine.scene.forEachEntityOfType<GiLightSource> { it.drawLightSource(engine, surface) }
        engine.scene.forEachEntityOfType<GiOccluder> { it.drawOccluder(engine, surface) }
    }

    private fun setTargetSurface(engine: PulseEngine)
    {
        if (targetSurface == lastTargetSurface)
            return

        engine.gfx.getSurface(lastTargetSurface)?.deletePostProcessingEffect(GI_BLEND_EFFECT)
        engine.gfx.getSurface(targetSurface)?.addPostProcessingEffect(MultiplyEffect(GI_BLEND_EFFECT, GI_LIGHT_FINAL))

        lastTargetSurface = targetSurface
    }

    companion object
    {
        const val GI_LOCAL_SCENE    = "gi_local_scene"
        const val GI_GLOBAL_SCENE   = "gi_global_scene"
        const val GI_LOCAL_SDF      = "gi_local_sdf"
        const val GI_GLOBAL_SDF     = "gi_global_sdf"
        const val GI_LIGHT_RAW      = "gi_light_raw"
        const val GI_LIGHT_FINAL    = "gi_light_final"
        const val GI_BLEND_EFFECT   = "gi_blend_effect"
    }
}