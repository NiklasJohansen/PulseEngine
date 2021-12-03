package no.njoh.pulseengine.modules.scene.systems.rendering

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import kotlin.reflect.KClass

abstract class EntityRenderer : SceneSystem()
{
    @JsonIgnore
    protected val renderPasses = mutableListOf<RenderPass>()

    fun addRenderPass(renderPass: RenderPass) =
        renderPasses.add(renderPass)

    fun removeRenderPass(renderPass: RenderPass) =
        renderPasses.remove(renderPass)

    data class RenderPass(
        val surfaceName: String,
        val type: KClass<*>
    )
}