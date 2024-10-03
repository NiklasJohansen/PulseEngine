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
    @Prop(i = 1)                      var skyColor = Color(0.02f, 0.08f, 0.2f, 1f)
    @Prop(i = 2)                      var sunColor = Color(0.95f, 0.95f, 0.9f, 1f)
    @Prop(i = 3)                      var skyIntensity = 1f
    @Prop(i = 5)                      var sunIntensity = 1f
    @Prop(i = 6)                      var sunDistance = 10f
    @Prop(i = 7)                      var sunAngle = 0f
    @Prop(i = 8)                      var dithering = 0f
    @Prop(i = 9, min=0.01f, max=2f)   var textureScale = 1f
    @Prop(i = 10, min=0f)             var drawCascade = 0
    @Prop(i = 11, min=0f)             var maxCascades = 10
    @Prop(i = 12)                     var intervalLength = 1f
    @Prop(i = 13)                     var bilinearFix = true
    @Prop(i = 14)                     var forkFix = true
    @Prop(i = 15)                     var lightBounce = true

    override fun onCreate(engine: PulseEngine)
    {
        engine.gfx.createSurface(
            name = SCENE_SURFACE_NAME,
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
            addPostProcessingEffect(SceneBounce())
        }

        engine.gfx.createSurface(
            name = DISTANCE_FIELD_SURFACE_NAME,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 2,
            isVisible = false,
            drawWhenEmpty = true,
            backgroundColor = Color.BLANK,
            textureFormat = RGBA32F,
            textureFilter = NEAREST,
            blendFunction = NONE,
            textureScale =  textureScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(JfaSeed())
            addPostProcessingEffect(Jfa())
            addPostProcessingEffect(DistanceField())
        }

        engine.gfx.createSurface(
            name = LIGHT_SURFACE_NAME,
            camera = engine.gfx.mainCamera,
            zOrder = engine.gfx.mainSurface.config.zOrder + 1,
            isVisible = false,
            drawWhenEmpty = true,
            backgroundColor = Color.BLANK,
            textureFormat = RGBA16F,
            textureFilter = LINEAR,
            blendFunction = NONE,
            textureScale = textureScale,
            attachments = listOf(COLOR_TEXTURE_0)
        ).apply {
            addPostProcessingEffect(RadianceCascades())
        }

        engine.gfx.mainSurface.addPostProcessingEffect(Compose())
    }

    override fun onUpdate(engine: PulseEngine)
    {
        engine.gfx.getSurface(SCENE_SURFACE_NAME)?.setTextureScale(textureScale)
        engine.gfx.getSurface(DISTANCE_FIELD_SURFACE_NAME)?.setTextureScale(textureScale)
        engine.gfx.getSurface(LIGHT_SURFACE_NAME)?.setTextureScale(textureScale)
    }

    override fun onRender(engine: PulseEngine)
    {
        val sceneSurface = engine.gfx.getSurface(SCENE_SURFACE_NAME) ?: return
        val sourceRenderer = sceneSurface.getRenderer<SceneRenderer>() ?: return

        drawScene(engine, sceneSurface, sourceRenderer)
    }

    override fun onDestroy(engine: PulseEngine)
    {
        engine.gfx.mainSurface.deletePostProcessingEffect("compose")
        engine.gfx.deleteSurface(LIGHT_SURFACE_NAME)
        engine.gfx.deleteSurface(DISTANCE_FIELD_SURFACE_NAME)
        engine.gfx.deleteSurface(SCENE_SURFACE_NAME)
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

    fun getLightSurface(engine: PulseEngine) = engine.gfx.getSurface(LIGHT_SURFACE_NAME)

    fun getSceneSurface(engine: PulseEngine) = engine.gfx.getSurface(SCENE_SURFACE_NAME)

    fun getSdfSurface(engine: PulseEngine) = engine.gfx.getSurface(DISTANCE_FIELD_SURFACE_NAME)

    companion object
    {
        private const val DISTANCE_FIELD_SURFACE_NAME = "sdf_surface"
        private const val SCENE_SURFACE_NAME = "scene_surface"
        private const val LIGHT_SURFACE_NAME = "light_surface"
    }
}