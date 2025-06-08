package no.njoh.pulseengine.core.scene.systems

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.scene.interfaces.Renderable
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import java.util.*

/**
 * Base class for all entity renderers.
 * Enables other systems to add render passes to be executed bye the renderer.
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
     * @param targetType The type of entities to draw.
     * @param drawCondition A per-entity condition that determines if an entity will be rendered.
     * @param drawFunction A custom function to draw the entities, if null the default onRender will be used.
     */
    data class RenderPass(
        val surfaceName: String,
        val targetType: Class<*>,
        val drawCondition: ((Any) -> Boolean)? = null,
        val drawFunction: ((PulseEngine, Surface, Any) -> Unit)? = null
    ) {
        companion object
        {
            @Suppress("UNCHECKED_CAST")
            inline operator fun <reified T : Any> invoke(
                surfaceName: String,
                noinline drawCondition: ((T) -> Boolean)? = null,
                noinline drawFunction: ((PulseEngine, Surface, T) -> Unit)? = null
            ) = RenderPass(
                surfaceName = surfaceName,
                targetType = T::class.java,
                drawCondition = drawCondition as ((Any) -> Boolean)?,
                drawFunction = drawFunction as ((PulseEngine, Surface, Any) -> Unit)?
            )
        }
    }
}

@Name("Entity Renderer")
@Icon("MONITOR")
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
                            task.entities += it as Renderable
                        }
                    }
                }
            }

            if (task.entities.isNotEmpty()) renderQueue.add(task) else taskPool.push(task)
        }
    }

    private fun createRenderTask(renderPass: RenderPass): RenderTask
    {
        if (taskPool.isNotEmpty())
        {
            val task = taskPool.pop()
            task.surfaceName = renderPass.surfaceName
            task.drawFunction = renderPass.drawFunction
            return task
        }
        return RenderTask(renderPass.surfaceName, renderPass.drawFunction)
    }

    private fun drawRenderQueue(engine: PulseEngine)
    {
        renderQueue.forEachFast { task ->
            val surface = engine.gfx.getSurface(task.surfaceName) ?: return@forEachFast
            val entities = task.entities
            val drawFunction = task.drawFunction
            if (entities.isNotEmpty())
            {
                entities.sortWith(BackToFrontEntityComparator)
                if (drawFunction != null)
                {
                    entities.forEachFast { drawFunction(engine, surface, it) }
                }
                else
                {
                    entities.forEachFast { it.onRender(engine, surface) }
                }
                entities.clear()
            }
            taskPool.push(task)
        }

        renderQueue.clear()
    }

    /**
     * Represents a single render pass of a collection of entities to a certain [Surface].
     */
    private data class RenderTask(
        var surfaceName: String,
        var drawFunction: ((PulseEngine, Surface, Any) -> Unit)?,
        val entities: ArrayList<Renderable> = ArrayList()
    )

    private object BackToFrontEntityComparator : Comparator<Renderable>
    {
        override fun compare(a: Renderable, b: Renderable): Int = ((b.z - a.z) * 10_000f).toInt()
    }
}