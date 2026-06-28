package no.njoh.pulseengine.modules.scene.systems

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment
import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContextInternal
import no.njoh.pulseengine.core.graphics.scene3d.shadow.CascadedShadowMapRenderer
import no.njoh.pulseengine.core.graphics.scene3d.shadow.LocalShadowAtlasRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ModelRenderer
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.EnvMapRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

@Icon("LIGHT_BULB")
@Name("Scene Lighting (3D)")
class Scene3DLightingSystem : SceneSystem()
{
    @Prop(i=0, min=0f)           var sunIntensity                  = 1f
    @Prop(i=1)                   var sunColor                      = Color(1f, 1f, 1f)
    @Prop(i=2, min=0f, max=360f) var sunDirection                  = 0f
    @Prop(i=3, min=0f, max=90f)  var sunHeight                     = 70f
    @Prop(i=4, min=0f)           var sunRadius                     = 1.5f
    @Prop(i=5, min=1f)           var sunShadowMapResolution        = 4096
    @Prop(i=6, min=0f, max=1f)   var sunShadowCascadeSplitLambda   = 0.5f
    @Prop(i=7, min=0f)           var sunShadowDistance             = 50f
    @Prop(i=8, min=0f)           var envIntensity                  = 1f
    @Prop(i=9)  @EnvMapRef       var envDiffuseTexture             = ""
    @Prop(i=10) @EnvMapRef       var envSpecularTexture            = ""
    @Prop(i=11)                  var targetSurfaces                = "scene3d"
    @Prop(i=12)                  var localShadowsEnabled           = true
    @Prop(i=13, min=256f)        var localShadowAtlasResolution    = 4096
    @Prop(i=14, min=64f)         var localShadowTileResolution     = 512
    @Prop(i=15, min=0f)          var localShadowMaxUpdatesPerFrame = 3

    private var shadowMapSurfaceName        = ""
    private var localShadowAtlasSurfaceName = ""
    private var lastTargetSurfaces          = ""
    private var targetSurfaceNames          = emptyList<String>()
    private var lastLocalAtlasResolution    = 0

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
            renderer?.sunRadius                   = sunRadius
            renderer?.sunShadowMapSurfaceName     = shadowMapSurfaceName
            renderer?.localShadowAtlasSurfaceName = localShadowAtlasSurfaceName
            renderer?.iblDiffuseTexture           = envDiffuseTexture
            renderer?.iblSpecularTexture          = envSpecularTexture
            renderer?.iblIntensity                = envIntensity
        }

        (engine.gfx.sceneContext as SceneRenderContextInternal).getLocalShadowAtlas().apply() 
        {
            enabled = localShadowsEnabled
            resolution = localShadowAtlasResolution
            shadowFaceResolution = localShadowTileResolution
            maxShadowUpdatesPerFrame = localShadowMaxUpdatesPerFrame
        }
    }

    override fun onRender(engine: PulseEngine)
    {
        val targetSurface = targetSurfaceNames.firstOrNull()?.let { engine.gfx.getSurface(it) } ?: return
        targetSurface.getRenderer<ModelRenderer>() ?: return
        val camera = targetSurface.camera

        setUpSunShadowMap(engine, camera)
        setUpLocalShadowAtlas(engine)
        collectLightSources(engine)
    }

    private fun setUpSunShadowMap(engine: PulseEngine, camera: Camera)
    {
        val shadowMapSurface = engine.gfx.getSurface(shadowMapSurfaceName)
        if (shadowMapSurface == null)
        {
            val index = 1 + (engine.gfx.getAllSurfaces().maxOfOrNull { it.config.name.substringAfterLast("_").toIntOrNull() ?: 0 } ?: 0)
            shadowMapSurfaceName = "global_shadow_map_$index"

            engine.gfx.createSurface(
                name = shadowMapSurfaceName,
                width = sunShadowMapResolution,
                height = sunShadowMapResolution,
                isVisible = false,
                zOrder = 50,
                attachments = listOf(Attachment.DEPTH_TEXTURE),
                textureSizeFunc = { _,_,_ -> PackedSize(sunShadowMapResolution, sunShadowMapResolution) }
            ).apply {
                addRenderer(CascadedShadowMapRenderer())
            }

            return // Return now, surface ready next frame
        }

        val shadowMapRenderer = shadowMapSurface.getRenderer<CascadedShadowMapRenderer>() ?: return

        shadowMapRenderer.resolution       = sunShadowMapResolution
        shadowMapRenderer.splitLambda      = sunShadowCascadeSplitLambda
        shadowMapRenderer.shadowDistance   = sunShadowDistance
        shadowMapRenderer.setFor(camera, sunDirection, sunHeight)
    }

    private fun setUpLocalShadowAtlas(engine: PulseEngine)
    {
        if (!localShadowsEnabled)
        {
            if (localShadowAtlasSurfaceName.isNotBlank())
                engine.gfx.deleteSurface(localShadowAtlasSurfaceName)
            localShadowAtlasSurfaceName = ""
            lastLocalAtlasResolution = 0
            return
        }

        val atlasSurface = engine.gfx.getSurface(localShadowAtlasSurfaceName)
        if (atlasSurface == null || localShadowAtlasResolution != lastLocalAtlasResolution)
        {
            if (localShadowAtlasSurfaceName.isNotBlank())
                engine.gfx.deleteSurface(localShadowAtlasSurfaceName)

            val index = 1 + (engine.gfx.getAllSurfaces().maxOfOrNull { it.config.name.substringAfterLast("_").toIntOrNull() ?: 0 } ?: 0)
            localShadowAtlasSurfaceName = "local_shadow_atlas_$index"
            lastLocalAtlasResolution = localShadowAtlasResolution

            engine.gfx.createSurface(
                name = localShadowAtlasSurfaceName,
                width = localShadowAtlasResolution,
                height = localShadowAtlasResolution,
                isVisible = false,
                clearColor = null, // Dont clear surface each frame
                zOrder = 49,
                attachments = listOf(Attachment.DEPTH_TEXTURE),
                textureSizeFunc = { _,_,_ -> PackedSize(localShadowAtlasResolution, localShadowAtlasResolution) }
            ).apply {
                addRenderer(LocalShadowAtlasRenderer())
            }
        }
    }

    private fun collectLightSources(engine: PulseEngine)
    {
        val context = engine.gfx.sceneContext
        engine.scene.forEachEntityOfType<Scene3DLightSource>()
        {
            if ((it as SceneEntity).isNot(HIDDEN)) it.onRenderLight(engine, context)
        }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        for (surface in targetSurfaceNames)
        {
            val renderer = engine.gfx.getSurface(surface)?.getRenderer<ModelRenderer>()
            renderer?.sunShadowMapSurfaceName = ""
            renderer?.localShadowAtlasSurfaceName = ""
            renderer?.iblDiffuseTexture       = ""
            renderer?.iblSpecularTexture      = ""
            renderer?.iblIntensity            = 0f
        }
        lastTargetSurfaces = ""
        targetSurfaceNames = emptyList()

        engine.gfx.deleteSurface(shadowMapSurfaceName)
        engine.gfx.deleteSurface(localShadowAtlasSurfaceName)
        shadowMapSurfaceName = ""
        localShadowAtlasSurfaceName = ""
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }

    @JsonIgnore
    fun getShadowMapSurfaceName() = shadowMapSurfaceName

    @JsonIgnore
    fun getLocalShadowAtlasSurfaceName() = localShadowAtlasSurfaceName
}

interface Scene3DLightSource
{
    fun onRenderLight(engine: PulseEngine, context: SceneRenderContext)
}