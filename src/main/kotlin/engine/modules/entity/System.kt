package engine.modules.entity

import engine.EngineInterface
import java.util.*

sealed class ComponentSystem(
    vararg val componentTypes: ComponentType<out Component>
) {

    var componentSignature = 0L
        private set

    // Creates a bit mask containing which component types this system will process
    fun updateComponentSignature(componentRegister: EnumMap<ComponentID, Int>)
    {
        componentSignature = 0L
        componentTypes.forEach {
            componentRegister[it.id]?.let { index ->
                componentSignature = componentSignature or (1 shl index).toLong()
            }
        }
    }

    abstract fun tick(engine: EngineInterface, entities: EntityCollection)
}

abstract class LogicSystem(
    vararg componentTypes: ComponentType<out Component>
) : ComponentSystem(*componentTypes) {
    override fun tick(engine: EngineInterface, entities: EntityCollection) = update(engine, entities)
    abstract fun update(engine: EngineInterface, entities: EntityCollection)
}


abstract class RenderSystem(
    vararg componentTypes: ComponentType<out Component>
) : ComponentSystem(*componentTypes) {
    override fun tick(engine: EngineInterface, entities: EntityCollection) = render(engine, entities)
    abstract fun render(engine: EngineInterface, entities: EntityCollection)
}