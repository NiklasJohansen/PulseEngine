package no.njoh.pulseengine.modules.scene.systems

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.api.Attachment
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.Frustum
import no.njoh.pulseengine.core.graphics.api.LightList
import no.njoh.pulseengine.core.graphics.api.WorldRenderFrame
import no.njoh.pulseengine.core.graphics.api.WorldRenderState
import no.njoh.pulseengine.core.graphics.renderers.CascadedShadowMapRenderer
import no.njoh.pulseengine.core.graphics.renderers.ModelRenderer
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
    @Prop(i=0, min=0f)           var sunIntensity                = 1f
    @Prop(i=1)                   var sunColor                    = Color(1f, 1f, 1f)
    @Prop(i=2, min=0f, max=360f) var sunDirection                = 0f
    @Prop(i=3, min=0f, max=90f)  var sunHeight                   = 70f
    @Prop(i=4, min=0f)           var sunRadius                   = 1.5f
    @Prop(i=5, min=1f)           var sunShadowMapResolution      = 4096
    @Prop(i=6, min=0f, max=1f)   var sunShadowCascadeSplitLambda = 0.5f
    @Prop(i=7, min=0f)           var sunShadowDistance           = 50f
    @Prop(i=8, min=0f)           var envIntensity                = 1f
    @Prop(i=9)  @EnvMapRef       var envDiffuseTexture           = ""
    @Prop(i=10) @EnvMapRef       var envSpecularTexture          = ""
    @Prop(i=11)                  var targetSurfaces              = "world"

    private var shadowMapSurfaceName = ""
    private var lastTargetSurfaces   = ""
    private var targetSurfaceNames   = emptyList<String>()
    private var lightFrustum         = Frustum()
    private var lightList            = LightList()
    private val shadowFrames         = Array(2) { WorldRenderFrame() }
    private var shadowFrameIndex     = 0

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
            val renderer = engine.gfx.getSurface(it)?.getRenderer<ModelRenderer>()
            renderer?.sunColor?.setFrom(sunColor)?.multiplyRgb(sunIntensity)
            renderer?.sunRadius               = sunRadius
            renderer?.sunShadowMapSurfaceName = shadowMapSurfaceName
            renderer?.iblDiffuseTexture       = envDiffuseTexture
            renderer?.iblSpecularTexture      = envSpecularTexture
            renderer?.iblIntensity            = envIntensity
        }
    }

    override fun onRender(engine: PulseEngine)
    {
        val targetSurface = targetSurfaceNames.firstOrNull()?.let { engine.gfx.getSurface(it) } ?: return
        val worldRenderState = engine.scene.getSystemOfType<WorldRenderSystem>()?.getRenderState() ?: return
        val camera = targetSurface.camera

        renderShadowMap(engine, camera, worldRenderState)
        renderWorldLights(engine, camera)
    }

    private fun renderShadowMap(engine: PulseEngine, camera: Camera, worldRenderState: WorldRenderState)
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
                addRenderer(CascadedShadowMapRenderer(worldRenderState))
            }

            return // Return now, surface ready next frame
        }

        val shadowMapRenderer = shadowMapSurface.getRenderer<CascadedShadowMapRenderer>() ?: return
        shadowMapRenderer.worldRenderState = worldRenderState
        shadowMapRenderer.resolution       = sunShadowMapResolution
        shadowMapRenderer.splitLambda      = sunShadowCascadeSplitLambda
        shadowMapRenderer.shadowDistance   = sunShadowDistance
        shadowMapRenderer.setFor(camera, sunDirection, sunHeight)

        val frame = shadowFrames[shadowFrameIndex]
        shadowFrameIndex = (shadowFrameIndex + 1) and 1
        frame.clear()

        engine.scene.forEachEntityOfType<WorldShadowCaster>()
        {
            if (it.castShadows && (it as SceneEntity).isNot(HIDDEN)) it.onRender(engine, frame)
        }

        shadowMapRenderer.draw(frame)
    }

    private fun renderWorldLights(engine: PulseEngine, camera: Camera)
    {
        lightList.reset()

        engine.scene.forEachEntityOfType<WorldLight>()
        {
            if ((it as SceneEntity).isNot(HIDDEN)) it.onRender(engine, lightList)
        }

        lightFrustum.setForCamera(camera)
        val culledLights = lightList.getFrustumCulledList(lightFrustum)

        culledLights.sortBy { camera.position.distanceSquared(it.position) }

        for (surface in targetSurfaceNames)
        {
            val renderer = engine.gfx.getSurface(surface)?.getRenderer<ModelRenderer>() ?: continue
            culledLights.forEachFast { renderer.addLight(it.position, it.direction, it.radius, it.color, it.intensity, it.outerConeAngle, it.innerConeAngle) }
        }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        for (surface in targetSurfaceNames)
        {
            val renderer = engine.gfx.getSurface(surface)?.getRenderer<ModelRenderer>()
            renderer?.sunShadowMapSurfaceName = ""
            renderer?.iblDiffuseTexture       = ""
            renderer?.iblSpecularTexture      = ""
            renderer?.iblIntensity            = 0f
        }
        lastTargetSurfaces = ""
        targetSurfaceNames = emptyList()

        engine.gfx.deleteSurface(shadowMapSurfaceName)
        shadowMapSurfaceName = ""
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }

    @JsonIgnore
    fun getShadowMapSurfaceName() = shadowMapSurfaceName
}

interface WorldShadowCaster
{
    var castShadows: Boolean

    fun onRender(engine: PulseEngine, frame: WorldRenderFrame)
}

interface WorldLight
{
    fun onRender(engine: PulseEngine, list: LightList)
}