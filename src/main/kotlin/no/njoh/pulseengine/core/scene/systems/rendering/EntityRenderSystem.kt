package no.njoh.pulseengine.core.scene.systems.rendering

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.entities.SceneEntity
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.widgets.editor.Name
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

@Name("Entity Renderer")
open class EntityRenderSystem : EntityRenderer()
{
    private val renderQueue = mutableListOf<RenderTask>()
    private val taskPool = Stack<RenderTask>()

    override fun onCreate(engine: PulseEngine)
    {
        addRenderPass(RenderPass(
            surfaceName = engine.gfx.mainSurface.name,
            targetType = SceneEntity::class
        ))
    }

    override fun onRender(engine: PulseEngine)
    {
        buildRenderQueue(engine)
        drawRenderQueue(engine)
    }

    private fun buildRenderQueue(engine: PulseEngine)
    {
        renderPasses.forEachFast { renderPass ->
            val task = createRenderTask(renderPass)
            engine.scene.forEachEntityTypeList { typeList ->
                if (renderPass.targetType.isInstance(typeList[0]))
                {
                    typeList.forEachFast { entity ->
                        val zOrder = entity.z.toInt()
                        var layer = task.layers[zOrder]
                        if (layer == null)
                        {
                            layer = Layer()
                            task.layers[zOrder] = layer
                        }
                        layer.entities.add(entity)
                    }
                }
            }

            if (task.layers.isNotEmpty()) renderQueue.add(task) else taskPool.push(task)
        }
    }

    private fun createRenderTask(renderPass: RenderPass): RenderTask = when
    {
        taskPool.empty() -> RenderTask(renderPass.surfaceName, renderPass.targetType)
        else -> taskPool.pop().apply {
            surfaceName = renderPass.surfaceName
            type = renderPass.targetType
        }
    }

    private fun drawRenderQueue(engine: PulseEngine)
    {
        renderQueue.forEachFast { task ->
            val isCustomRenderTarget = task.type.isSubclassOf(CustomRenderPassTarget::class)
            val surface = engine.gfx.getSurfaceOrDefault(task.surfaceName)
            task.layers
                .toSortedMap(reverseOrder())
                .forEach { (_, layer) ->
                    if (layer.entities.isNotEmpty())
                    {
                        if (isCustomRenderTarget)
                            layer.entities.forEachFast { (it as CustomRenderPassTarget).renderCustomPass(engine, surface) }
                        else
                            layer.entities.forEachFast { it.onRender(engine, surface) }

                        layer.entities.clear()
                        layer.emptyFrames = 0
                    }
                    else layer.emptyFrames++
                }

            task.layers.values.removeIf { it.emptyFrames >= 10 }
            taskPool.push(task)
        }
        renderQueue.clear()
    }

    /**
     * Represents a single render pass of a collection of layers to a certain [Surface]
     */
    private data class RenderTask(
        var surfaceName: String,
        var type: KClass<*>,
        val layers: MutableMap<Int, Layer> = mutableMapOf()
    )

    /**
     * Represents a collection of layers at a certain Z-depth.
     */
    private data class Layer(
        val entities: ArrayList<SceneEntity> = ArrayList(),
        var emptyFrames: Int = 0
    )
}