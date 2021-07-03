package no.njoh.pulseengine.modules.scene.systems.default

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.scene.SurfaceName
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.util.firstOrNullFast
import no.njoh.pulseengine.widgets.sceneEditor.Name

@Name("Entity Renderer")
open class EntityRenderSystem : SceneSystem()
{
    override fun onRender(engine: PulseEngine)
    {
        val gfx = engine.gfx
        engine.scene.forEachEntityTypeList { typeList ->
            if (typeList.isNotEmpty())
            {
                val surface = typeList[0]::class.annotations
                    .firstOrNullFast { it is SurfaceName }
                    ?.let { gfx.getSurface2D((it as SurfaceName).name) }
                    ?: gfx.mainSurface

                typeList.forEachFast { it.onRender(engine, surface) }
            }
        }
    }
}