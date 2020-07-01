package engine.modules.entity

import engine.PulseEngine

sealed class ComponentSystem(vararg val componentTypes: ComponentType<out Component>)
{
    var componentSignature = 0L
        private set

    // Creates a bit mask containing which component types this system will process
    fun updateComponentSignature()
    {
        componentSignature = 0L
        componentTypes.forEach {
            componentSignature = componentSignature or (1 shl it.index).toLong()
        }
    }

    abstract fun tick(engine: PulseEngine, entities: EntityCollection)
}

abstract class LogicSystem(vararg types: ComponentType<out Component>) : ComponentSystem(*types)
{
    override fun tick(engine: PulseEngine, entities: EntityCollection) = update(engine, entities)
    abstract fun update(engine: PulseEngine, entities: EntityCollection)
}

abstract class RenderSystem(vararg types: ComponentType<out Component>) : ComponentSystem(*types)
{
    override fun tick(engine: PulseEngine, entities: EntityCollection) = render(engine, entities)
    abstract fun render(engine: PulseEngine, entities: EntityCollection)
}