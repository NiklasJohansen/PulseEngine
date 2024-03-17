package no.njoh.pulseengine.core.scene.systems

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.scene.interfaces.Renderable
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

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
        val targetType: KClass<*>,
        val drawCondition: ((Any) -> Boolean)? = null
    )
}

/**
 * Marks a [SceneEntity] as a target for custom render passes performed by the [EntityRenderer].
 */
interface CustomRenderPassTarget
{
    fun renderCustomPass(engine: PulseEngine, surface: Surface2D)
}

@Name("Entity Renderer")
@ScnIcon("MONITOR")
open class EntityRendererImpl : EntityRenderer()
{
    private val renderQueue = mutableListOf<RenderTask>()
    private val taskPool = Stack<RenderTask>()

    override fun onCreate(engine: PulseEngine)
    {
        addRenderPass(RenderPass(
            surfaceName = engine.gfx.mainSurface.name,
            targetType = Renderable::class
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
            val condition = renderPass.drawCondition
            engine.scene.forEachEntityTypeList { typeList ->
                if (typeList.firstOrNull() is Renderable && renderPass.targetType.isInstance(typeList.firstOrNull()))
                {
                    typeList.forEachFast()
                    {
                        if (it.isNot(HIDDEN) && (condition == null || condition(it)))
                        {
                            val entity = it as Renderable
                            val zOrder = entity.z.toInt()
                            task.layers.getOrPut(zOrder) { Layer() }.entities.add(entity)
                        }
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
     * Represents a single render pass of a collection of layers to a certain [Surface].
     */
    private data class RenderTask(
        var surfaceName: String,
        var type: KClass<*>,
        val layers: MutableMap<Int, Layer> = mutableMapOf()
    )

    /**
     * Represents a collection of entities at a certain Z-depth.
     */
    private data class Layer(
        val entities: ArrayList<Renderable> = ArrayList(),
        var emptyFrames: Int = 0
    )
}
