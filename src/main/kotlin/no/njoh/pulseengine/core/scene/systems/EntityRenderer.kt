package no.njoh.pulseengine.core.scene.systems

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.scene.interfaces.Renderable
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import java.util.*

/**
 * Base class for all entity renderers.
 * Enables other systems to add render passes to be performed bye the renderer.
 */
abstract class EntityRenderer : SceneSystem()
{
    @JsonIgnore
    protected val renderPasses = mutableListOf<RenderPass>()

    /** Adds a [RenderPass] to the [EntityRenderer] */
    fun addRenderPass(renderPass: RenderPass) =
        renderPasses.add(renderPass)

    /** Removes a [RenderPass] from the [EntityRenderer] */
    fun removeRenderPass(renderPass: RenderPass) =
        renderPasses.remove(renderPass)

    /**
     * Represents a single render pass.
     *
     * @param surfaceName The name of the surface to draw the entities to.
     * @param targetType The type of entities to draw
     * @param drawCondition A per-entity condition that determines if an entity will be rendered.
     */
    data class RenderPass(
        val surfaceName: String,
        val targetType: Class<*>,
        val drawCondition: ((Any) -> Boolean)? = null
    )
}

/**
 * Marks a [SceneEntity] as a target for custom render passes performed by the [EntityRenderer].
 */
interface CustomRenderPassTarget
{
    fun renderCustomPass(engine: PulseEngine, surface: Surface)
}

@Name("Entity Renderer")
@ScnIcon("MONITOR")
open class EntityRendererImpl : EntityRenderer()
{
    private val renderQueue = mutableListOf<RenderTask>()
    private val taskPool = Stack<RenderTask>()

    override fun onCreate(engine: PulseEngine)
    {
        addRenderPass(RenderPass(surfaceName = engine.gfx.mainSurface.config.name, targetType = Renderable::class.java))
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
            val condition = renderPass.drawCondition
            engine.scene.forEachEntityTypeList { typeList ->
                val first = typeList.firstOrNull()
                if (first is Renderable && renderPass.targetType.isInstance(first))
                {
                    typeList.forEachFast()
                    {
                        if (it.isNot(HIDDEN) && (condition == null || condition(it)))
                        {
                            task.addEntity(it as Renderable)
                        }
                    }
                }
            }

            if (task.layers.isNotEmpty()) renderQueue.add(task) else taskPool.push(task)
        }
    }

    private fun createRenderTask(renderPass: RenderPass): RenderTask
    {
        if (taskPool.isNotEmpty())
        {
            val task = taskPool.pop()
            task.surfaceName = renderPass.surfaceName
            task.type = renderPass.targetType
            return task
        }

        return RenderTask(renderPass.surfaceName, renderPass.targetType)
    }

    private fun drawRenderQueue(engine: PulseEngine)
    {
        renderQueue.forEachFast { task ->
            val isCustomRenderTarget = CustomRenderPassTarget::class.java.isAssignableFrom(task.type)
            val surface = engine.gfx.getSurface(task.surfaceName) ?: return@forEachFast

            task.layers.sortWith(BackToFrontComparator)
            task.layers.forEachFast { layer ->
                val entities = layer.entities
                if (entities.isNotEmpty())
                {
                    if (isCustomRenderTarget)
                    {
                        entities.forEachFast { (it as CustomRenderPassTarget).renderCustomPass(engine, surface) }
                    }
                    else
                    {
                        entities.forEachFast { it.onRender(engine, surface) }
                    }
                    entities.clear()
                    layer.emptyFrames = 0
                }
                else layer.emptyFrames++
            }

            task.removeEmptyLayers()
            taskPool.push(task)
        }
        renderQueue.clear()
    }

    /**
     * Represents a single render pass of a collection of layers to a certain [Surface].
     */
    private data class RenderTask(
        var surfaceName: String,
        var type: Class<*>
    ) {
        val layers = ArrayList<Layer>(10)
        val layerMap = HashMap<Int, Layer>()

        fun addEntity(entity: Renderable)
        {
            val zOrder = entity.z.toInt()
            var layer = layerMap[zOrder]
            if (layer == null)
            {
                layer = Layer(zOrder)
                layers.add(layer)
                layerMap[zOrder] = layer
            }
            layer.entities.add(entity)
        }

        fun removeEmptyLayers()
        {
            layers.removeWhen()
            {
                val shouldRemove = (it.emptyFrames >= 10)
                if (shouldRemove)
                    layerMap.remove(it.zOrder)
                shouldRemove
            }
        }
    }

    /**
     * Represents a collection of entities at a certain Z-depth.
     */
    private data class Layer(
        val zOrder: Int,
        val entities: ArrayList<Renderable> = ArrayList(),
        var emptyFrames: Int = 0
    )

    private object BackToFrontComparator : Comparator<Layer>
    {
        override fun compare(a: Layer, b: Layer): Int = b.zOrder - a.zOrder
    }
}