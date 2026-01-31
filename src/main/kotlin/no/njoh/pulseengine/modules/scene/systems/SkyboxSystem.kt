package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.renderers.SkyboxRenderer
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.EnvMapRef
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

@Name("Skybox")
class SkyboxSystem : SceneSystem()
{
    @Prop(i=0, min=0f)    var intensity          = 1f
    @Prop(i=1) @EnvMapRef var skyboxTexture      = ""
    @Prop(i=2) @EnvMapRef var envSpecularTexture = ""
    @Prop(i=3) @EnvMapRef var envDiffuseTexture  = ""
    @Prop(i=4)            var targetSurfaces     = "world"

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
            surface.addRenderer(SkyboxRenderer(), 0)
            return
        }

        renderer.brightness = intensity
        renderer.envTexture = skyboxTexture
        renderer.envSpecularTexture = envSpecularTexture
        renderer.envDiffuseTexture = envDiffuseTexture
    }
}