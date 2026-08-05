package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.EnvMap
import no.njoh.pulseengine.core.graphics.scene3d.renderers.SkyboxRenderer
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

@Name("3D Skybox")
class SkyboxSystem : SceneSystem()
{
    @Prop(i=0, min=0f) var brightness     = 0.5f
    @Prop(i=1)         var texture        = AssetHandle<EnvMap>()
    @Prop(i=4)         var targetSurfaces = "scene3d"

    private var lastTargetSurfaces = ""
    private var targetSurfaceNames = emptyList<String>()

    override fun onUpdate(engine: PulseEngine)
    {
        if (targetSurfaces != lastTargetSurfaces)
        {
            onDestroy(engine)
            lastTargetSurfaces = targetSurfaces
            targetSurfaceNames = targetSurfaces.split(",").map { it.trim() }
        }

        targetSurfaceNames.forEachFast { updateRenderer(engine, it) }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        for (surfaceName in targetSurfaceNames)
        {
            val surface = engine.gfx.getSurface(surfaceName) ?: continue
            val renderer = surface.getRenderer<SkyboxRenderer>() ?: continue
            surface.deleteRenderer(renderer)
        }
        lastTargetSurfaces = ""
        targetSurfaceNames = emptyList()
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (!enabled) onDestroy(engine)
    }

    private fun updateRenderer(engine: PulseEngine, surfaceName: String)
    {
        val surface = engine.gfx.getSurface(surfaceName) ?: return
        val renderer = surface.getRenderer<SkyboxRenderer>()
        if (renderer == null)
        {
            surface.addRenderer(SkyboxRenderer())
            return
        }

        renderer.brightness = brightness
        renderer.envTexture = texture.name
    }
}