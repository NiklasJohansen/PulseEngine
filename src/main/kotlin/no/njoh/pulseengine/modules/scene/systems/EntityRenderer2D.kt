package no.njoh.pulseengine.modules.scene.systems

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.scene.interfaces.Renderable2D
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.quickSort
import java.util.*

/**
 * Base class for all entity renderers.
 * Enables other systems to add render passes to be executed by the renderer.
 */
abstract class EntityRenderer2D : SceneSystem()
{
    @JsonIgnore
    protected val entityRenderPasses = mutableListOf<EntityRenderPass>()

    /** Adds a new [EntityRenderPass] to the [EntityRenderer2D] */
    inline fun <reified T : Any> addRenderPass(
        surfaceName: String,
        noinline drawCondition: ((T) -> Boolean)? = null,
        noinline drawFunction: (T.(PulseEngine, Surface) -> Unit)? = null
    ) {
        addRenderPass(EntityRenderPass<T>(surfaceName, drawCondition, drawFunction))
    }

    /** Adds the given [EntityRenderPass] to the [EntityRenderer2D] */
    fun addRenderPass(renderPass: EntityRenderPass) =
        entityRenderPasses.add(renderPass)

    /** Removes a [EntityRenderPass] from the [EntityRenderer2D] */
    fun removeRenderPass(renderPass: EntityRenderPass) =
        entityRenderPasses.remove(renderPass)

    /**
     * Represents a single render pass.
     *
     * @param surfaceName The name of the surface to draw the entities to.
     * @param targetType The type of entities to draw.
     * @param drawCondition A per-entity condition that determines if an entity will be rendered.
     * @param drawFunction A custom function to draw the entities, if null the default onRender will be used.
     */
    data class EntityRenderPass(
        val surfaceName: String,
        val targetType: Class<*>,
        val drawCondition: ((Any) -> Boolean)? = null,
        val drawFunction: (Any.(PulseEngine, Surface) -> Unit)? = null
    ) {
        companion object
        {
            @Suppress("UNCHECKED_CAST")
            inline operator fun <reified T : Any> invoke(
                surfaceName: String,
                noinline drawCondition: ((T) -> Boolean)? = null,
                noinline drawFunction: (T.(PulseEngine, Surface) -> Unit)? = null
            ) = EntityRenderPass(
                surfaceName = surfaceName,
                targetType = T::class.java,
                drawCondition = drawCondition as ((Any) -> Boolean)?,
                drawFunction = drawFunction as (Any.(PulseEngine, Surface) -> Unit)?
            )
        }
    }
}

@Name("2D Entity Renderer")
@Icon("MONITOR")
open class EntityRendererImpl : EntityRenderer2D()
{
    private val renderQueue = mutableListOf<RenderTask>()
    private val taskPool = Stack<RenderTask>()

    override fun onCreate(engine: PulseEngine)
    {
        addRenderPass(EntityRenderPass(surfaceName = engine.gfx.mainSurface.config.name, targetType = Renderable2D::class.java))
    }

    override fun onRender(engine: PulseEngine)
    {
        buildRenderQueue(engine)
        drawRenderQueue(engine)
    }

    private fun buildRenderQueue(engine: PulseEngine)
    {
        entityRenderPasses.forEachFast { renderPass ->
            val task = createRenderTask(renderPass)
            val condition = renderPass.drawCondition
            engine.scene.forEachEntityTypeList { typeList ->
                val first = typeList.firstOrNull()
                if (first is Renderable2D && renderPass.targetType.isInstance(first))
                {
                    typeList.forEachFast()
                    {
                        if (it.isNot(HIDDEN) && (condition == null || condition(it)))
                        {
                            task.entities += it as Renderable2D
                        }
                    }
                }
            }

            if (task.entities.isNotEmpty()) renderQueue.add(task) else taskPool.push(task)
        }
    }

    private fun createRenderTask(renderPass: EntityRenderPass): RenderTask
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
                entities.quickSort(BackToFrontEntityComparator)
                when (drawFunction)
                {
                    null -> entities.forEachFast { it.onRender(engine, surface) }
                    else -> entities.forEachFast { drawFunction(it, engine, surface) }
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
        var drawFunction: (Any.(PulseEngine, Surface) -> Unit)?,
        val entities: ArrayList<Renderable2D> = ArrayList()
    )

    private object BackToFrontEntityComparator : Comparator<Renderable2D>
    {
        override fun compare(a: Renderable2D, b: Renderable2D): Int = ((b.z - a.z) * 10_000f).toInt()
    }
}