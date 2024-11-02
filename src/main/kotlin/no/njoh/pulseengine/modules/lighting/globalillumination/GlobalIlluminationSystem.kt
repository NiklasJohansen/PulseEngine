package no.njoh.pulseengine.modules.lighting.globalillumination

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.BlendFunction.NONE
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.modules.scene.entities.Wall
import no.njoh.pulseengine.modules.lighting.LightOccluder
import no.njoh.pulseengine.modules.lighting.LightSource
import no.njoh.pulseengine.modules.lighting.globalillumination.effects.*

import kotlin.math.*

@Name("Global Illumination (RC)")
@Icon("LIGHT_BULB")
open class GlobalIlluminationSystem : SceneSystem()
{
    @Prop(i = 0)                      var ambientLight = true
    @Prop(i = 1)                      var skyColor = Color(0.02f, 0.08f, 0.2f, 1f)
    @Prop(i = 2)                      var sunColor = Color(0.95f, 0.95f, 0.9f, 1f)
    @Prop(i = 3)                      var skyIntensity = 1f
    @Prop(i = 5)                      var sunIntensity = 1f
    @Prop(i = 6)                      var sunDistance = 10f
    @Prop(i = 7)                      var sunAngle = 0f
    @Prop(i = 8)                      var dithering = 0f
    @Prop(i = 9)                      var textureFilter = LINEAR
    @Prop(i = 10, min=0.01f, max=2f)  var textureScale = 1f
    @Prop(i = 11, min=0f)             var drawCascade = 0
    @Prop(i = 12, min=0f)             var maxCascades = 10
    @Prop(i = 13)                     var intervalLength = 1f
    @Prop(i = 14, min=1f)             var worldScale = 2f
    @Prop(i = 15)                     var traceWorldRays = true
    @Prop(i = 16)                     var bilinearFix = true
    @Prop(i = 17)                     var forkFix = true
    @Prop(i = 18)                     var lightBounce = true
    @Prop(i = 19)                     var fixJitter = true

    override fun onCreate(engine: PulseEngine)
    {
        // --------------------------------------- SCREEN SPACE SURFACE ---------------------------------------

        engine.gfx.createSurface(
            name = SCREEN_SURFACE,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 3,
            isVisible = false,
            backgroundColor = Color.BLANK,
            textureFormat = RGBA16F,
            blendFunction = NONE,
            textureFilter = NEAREST,
            attachments = listOf(COLOR_TEXTURE_0, COLOR_TEXTURE_1)
        ).apply {
            addRenderer(SceneRenderer((this as SurfaceInternal).config))
            addPostProcessingEffect(SceneBounce(LIGHT_OUTPUT_SURFACE))
        }

        engine.gfx.createSurface(
            name = SCREEN_DISTANCE_FIELD_SURFACE,
            zOrder = engine.gfx.mainSurface.config.zOrder + 2,
            isVisible = false,
            drawWhenEmpty = true,
            backgroundColor = Color.BLANK,
            textureScale =  textureScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(JfaSeed(SCREEN_SURFACE))
            addPostProcessingEffect(Jfa())
            addPostProcessingEffect(DistanceField())
        }

        // --------------------------------------- WORLD SPACE SURFACE ---------------------------------------

        engine.gfx.createSurface(
            name = WORLD_SURFACE,
            zOrder = engine.gfx.mainSurface.config.zOrder + 3,
            isVisible = false,
            backgroundColor = Color.BLANK,
            textureFormat = RGBA16F,
            blendFunction = NONE,
            textureFilter = NEAREST,
            attachments = listOf(COLOR_TEXTURE_0, COLOR_TEXTURE_1)
        ).apply {
            addRenderer(SceneRenderer((this as SurfaceInternal).config))
        }

        engine.gfx.createSurface(
            name = WORLD_DIST_FIELD_SURFACE,
            zOrder = engine.gfx.mainSurface.config.zOrder + 2,
            isVisible = false,
            drawWhenEmpty = true,
            backgroundColor = Color.BLANK,
            textureScale =  textureScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(JfaSeed(WORLD_SURFACE))
            addPostProcessingEffect(Jfa())
            addPostProcessingEffect(DistanceField())
        }

        // --------------------------------------- LIGHT OUTPUT SURFACE ---------------------------------------

        engine.gfx.createSurface(
            name = LIGHT_OUTPUT_SURFACE,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 1,
            isVisible = false,
            drawWhenEmpty = true,
            backgroundColor = Color.BLANK,
            textureScale = textureScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(
                RadianceCascades(
                    SCREEN_SURFACE,
                    SCREEN_DISTANCE_FIELD_SURFACE,
                    WORLD_SURFACE,
                    WORLD_DIST_FIELD_SURFACE
                )
            )
        }

        // Apply as post-processing effect to main surface
        engine.gfx.mainSurface.addPostProcessingEffect(Compose(LIGHT_OUTPUT_SURFACE))
    }

    override fun onUpdate(engine: PulseEngine)
    {
        engine.gfx.getSurface(SCREEN_SURFACE)?.setTextureScale(textureScale)
        engine.gfx.getSurface(SCREEN_SURFACE)?.getRenderer<SceneRenderer>()?.fixJitter = fixJitter
        engine.gfx.getSurface(SCREEN_DISTANCE_FIELD_SURFACE)?.setTextureScale(textureScale)

        engine.gfx.getSurface(WORLD_SURFACE)?.setTextureScale(textureScale)
        engine.gfx.getSurface(WORLD_SURFACE)?.getRenderer<SceneRenderer>()?.fixJitter = fixJitter
        engine.gfx.getSurface(WORLD_DIST_FIELD_SURFACE)?.setTextureScale(textureScale)

        engine.gfx.getSurface(LIGHT_OUTPUT_SURFACE)?.setTextureScale(textureScale)
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (traceWorldRays)
        {
            val worldSurface = engine.gfx.getSurface(WORLD_SURFACE) ?: return
            val cam = worldSurface.camera
            val scale = 1f / max(1f, worldScale)

            cam.position.set(engine.gfx.mainCamera.position)
            cam.rotation.set(engine.gfx.mainCamera.rotation)
            cam.origin.set(worldSurface.config.width * 0.5f, worldSurface.config.height * 0.5f, 1f)
            cam.scale.set(engine.gfx.mainCamera.scale.x * scale, engine.gfx.mainCamera.scale.y * scale, engine.gfx.mainCamera.scale.z)
        }
    }

    override fun onRender(engine: PulseEngine)
    {
        val screenSurface = engine.gfx.getSurface(SCREEN_SURFACE) ?: return
        val screenSourceRenderer = screenSurface.getRenderer<SceneRenderer>() ?: return
        drawScene(engine, screenSurface, screenSourceRenderer)

        val worldSurface = engine.gfx.getSurface(WORLD_SURFACE) ?: return
        worldSurface.setDrawWhenEmpty(traceWorldRays) // Only draw world surface if we are tracing world rays
        if (traceWorldRays)
        {
            val worldSourceRenderer = worldSurface.getRenderer<SceneRenderer>() ?: return
            drawScene(engine, worldSurface, worldSourceRenderer)
        }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        engine.gfx.mainSurface.deletePostProcessingEffect("compose")
        engine.gfx.deleteSurface(SCREEN_SURFACE)
        engine.gfx.deleteSurface(WORLD_SURFACE)
        engine.gfx.deleteSurface(SCREEN_DISTANCE_FIELD_SURFACE)
        engine.gfx.deleteSurface(WORLD_DIST_FIELD_SURFACE)
        engine.gfx.deleteSurface(LIGHT_OUTPUT_SURFACE)
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }

    private fun drawScene(engine: PulseEngine, surface: Surface, renderer: SceneRenderer)
    {
        engine.scene.forEachEntityOfType<LightSource>()
        {
            val w = (it as? Spatial)?.width ?: (it.radius * 2f)
            val h = (it as? Spatial)?.height ?: (it.radius * 2f)
            if (it is SceneEntity && it.isNot(SceneEntity.HIDDEN))
            {
                surface.setDrawColor(it.color)
                renderer.drawTexture(
                    x = it.x,
                    y = it.y,
                    w = w,
                    h = h,
                    angle = it.rotation,
                    cornerRadius = min(w, h) * 0.5f,
                    intensity = it.intensity,
                    coneAngle = it.coneAngle
                )
            }
        }

        engine.scene.forEachEntityOfType<LightOccluder>
        {
            if (it is Wall) // TODO: don't use wall, make new LightOccluder interface for this
            {
                surface.setDrawColor(it.color)
                renderer.drawTexture(
                    x = if (it.xInterpolated.isNaN()) it.x else it.xInterpolated,
                    y = if (it.yInterpolated.isNaN()) it.y else it.yInterpolated,
                    w = it.width,
                    h = it.height,
                    angle = if (it.rotInterpolated.isNaN()) it.rotation else it.rotInterpolated,
                    cornerRadius = 0f,
                    intensity = 0f,
                    coneAngle = 360f
                )
            }
        }
    }

    companion object
    {
        private const val SCREEN_SURFACE                = "gi_screen"
        private const val WORLD_SURFACE                 = "gi_world"
        private const val SCREEN_DISTANCE_FIELD_SURFACE = "gi_screen_df"
        private const val WORLD_DIST_FIELD_SURFACE      = "gi_world_df"
        private const val LIGHT_OUTPUT_SURFACE          = "gi_light_output"
    }
}