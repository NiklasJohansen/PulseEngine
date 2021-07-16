package no.njoh.pulseengine.modules.scene.systems.default

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SwapList
import no.njoh.pulseengine.modules.scene.SurfaceName
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.util.firstOrNullFast
import no.njoh.pulseengine.widgets.sceneEditor.Name

@Name("Entity Renderer")
open class EntityRenderSystem : SceneSystem()
{
    private val layers = mutableMapOf<Int, SwapList<SceneEntity>>()
    private val zOrderToSurfaceName = mutableMapOf<Int, String>()

    override fun onRender(engine: PulseEngine)
    {
        val gfx = engine.gfx
        engine.scene.forEachEntityTypeList { typeList ->
            val surface = typeList[0]::class.annotations
                .firstOrNullFast { it is SurfaceName }
                ?.let { gfx.getSurfaceOrDefault((it as SurfaceName).name) }
                ?: gfx.mainSurface

            val surfaceZOrder = surface.zOrder * 1000
            zOrderToSurfaceName[surfaceZOrder] = surface.name

            typeList.forEachFast {
                val zOrder = surfaceZOrder + it.z.toInt()
                var list = layers[zOrder]
                if (list == null)
                {
                    list = SwapList()
                    layers[zOrder] = list
                }
                list.add(it)
            }
        }

        layers.toSortedMap(reverseOrder()).forEach { (zOrder, entities) ->
            val surfaceZOrder = zOrder / 1000 * 1000
            zOrderToSurfaceName[surfaceZOrder]?.let { surfaceName ->
                val surface = gfx.getSurfaceOrDefault(surfaceName)
                entities.forEachFast {
                    it.onRender(engine, surface)
                }
            }
            entities.swap()
        }

        zOrderToSurfaceName.clear()
        layers.values.removeIf {
            if (it.isEmpty())
                it.clear()
            it.isEmpty()
        }
    }
}