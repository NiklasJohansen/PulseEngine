package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.api.Attachment
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.LightList
import no.njoh.pulseengine.core.graphics.renderers.ModelRenderer
import no.njoh.pulseengine.core.graphics.renderers.ShadowMapRenderer
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.EnvMapRef
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

@Name("World Lighting (3D)")
class WorldLightingSystem : SceneSystem()
{
    @Prop(i=0, min=0f)           var sunIntensity              = 1f
    @Prop(i=1)                   var sunColor                  = Color(1f, 1f, 1f)
    @Prop(i=2, min=0f, max=360f) var sunDirection              = 0f
    @Prop(i=3, min=0f, max=90f)  var sunHeight                 = 70f
    @Prop(i=4, min=0f)           var sunRadius                 = 1f
    @Prop(i=5, min=1f)           var sunShadowMapMeters        = 15f
    @Prop(i=6, min=1f)           var sunShadowMapResolution    = 4096
    @Prop(i=7, min=1f)           var sunShadowContactHardening = true

    @Prop(i=8, min=0f)           var envIntensity              = 1f
    @Prop(i=9)  @EnvMapRef       var envSpecularTexture        = ""
    @Prop(i=10) @EnvMapRef       var envDiffuseTexture         = ""
    @Prop(i=11)                  var targetSurfaces            = "world"

    private var shadowMapSurfaceName = ""
    private var lastTargetSurfaces = ""
    private var targetSurfaceNames = emptyList<String>()
    private var frustum = Frustum()
    private var lightList = LightList()

    override fun onUpdate(engine: PulseEngine)
    {
        if (targetSurfaces != lastTargetSurfaces)
        {
            onDestroy(engine)
            lastTargetSurfaces = targetSurfaces
            targetSurfaceNames = targetSurfaces.split(",").map { it.trim() }
        }

        targetSurfaceNames.forEachFast() 
        {
            val r = engine.gfx.getSurface(it)?.getRenderer<ModelRenderer>()
            r?.shadowMapSurfaceName = shadowMapSurfaceName
            r?.iblDiffuseTexture    = envDiffuseTexture
            r?.iblSpecularTexture   = envSpecularTexture
            r?.iblIntensity         = envIntensity
        }
    }

    override fun onRender(engine: PulseEngine)
    {
        val camera = targetSurfaceNames.firstOrNull()?.let { engine.gfx.getSurface(it) }?.camera ?: return
        frustum.setForCamera(camera)
 
        renderShadowMap(engine, camera)
        renderWorldLights(engine, camera)
    }

    private fun renderShadowMap(engine: PulseEngine, camera: Camera)
    {
        val shadowMapSurface = engine.gfx.getSurface(shadowMapSurfaceName)
        if (shadowMapSurface == null)
        {
            val index = 1 + (engine.gfx.getAllSurfaces().maxOfOrNull { it.config.name.substringAfterLast("_").toIntOrNull() ?: 0 } ?: 0)
            shadowMapSurfaceName = "shadow_map_$index"

            engine.gfx.createSurface(
                name = shadowMapSurfaceName,
                width = sunShadowMapResolution,
                height = sunShadowMapResolution,
                isVisible = false,
                zOrder = 50,
                attachments = listOf(Attachment.DEPTH_TEXTURE),
                textureSizeFunc = { _,_,_ -> PackedSize(sunShadowMapResolution, sunShadowMapResolution) }
            ).apply {
                addRenderer(ShadowMapRenderer())
            }

            return // Return now, surface ready next frame
        }

        val shadowMapRenderer = shadowMapSurface.getRenderer<ShadowMapRenderer>() ?: return
        val shadowCasters = DrawList()

        engine.scene.forEachEntityOfType<WorldShadowCaster>()
        {
            if (it.castShadows && (it as SceneEntity).isNot(HIDDEN)) it.onRender(engine, shadowCasters)
        }

        shadowMapRenderer.updateSunParameters(camera.position, sunDirection, sunHeight)
        shadowMapRenderer.lightColor.setFrom(sunColor)
        shadowMapRenderer.lightIntensity = sunIntensity
        shadowMapRenderer.lightRadius = sunRadius
        shadowMapRenderer.shadowMapSizeMeters = sunShadowMapMeters
        shadowMapRenderer.shadowMapResolution = sunShadowMapResolution
        shadowMapRenderer.shadowContactHardening = sunShadowContactHardening

        frustum.setForViewProjection(shadowMapRenderer.getViewProjectionMatrix(read = false))

        val culledShadowCasters = shadowCasters.getFrustumCulledList(frustum)

        shadowMapRenderer.draw(culledShadowCasters)
    }

    private fun renderWorldLights(engine: PulseEngine, camera: Camera)
    {
        lightList.reset()

        engine.scene.forEachEntityOfType<WorldLight>()
        {
            if ((it as SceneEntity).isNot(HIDDEN)) it.onRender(engine, lightList)
        }

        val culledLights = lightList.getFrustumCulledList(frustum)

        culledLights.sortBy { camera.position.distanceSquared(it.position) }

        for (surface in targetSurfaceNames)
        {
            val renderer = engine.gfx.getSurface(surface)?.getRenderer<ModelRenderer>() ?: continue
            culledLights.forEachFast { renderer.addLight(it.position, it.radius, it.color, it.intensity) }
        }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        for (targetSurfaceName in targetSurfaceNames)
        {
            val r = engine.gfx.getSurface(targetSurfaceName)?.getRenderer<ModelRenderer>()
            r?.shadowMapSurfaceName = ""
            r?.iblDiffuseTexture    = ""
            r?.iblSpecularTexture   = ""
            r?.iblIntensity         = 0f
        }
        lastTargetSurfaces = ""
        targetSurfaceNames = emptyList()
        
        engine.gfx.deleteSurface(shadowMapSurfaceName)
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }
}

interface WorldShadowCaster
{
    var castShadows: Boolean

    fun onRender(engine: PulseEngine, drawList: DrawList)
}

interface WorldLight
{
    fun onRender(engine: PulseEngine, list: LightList)
}