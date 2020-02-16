package engine.modules.entity

import engine.EngineInterface

abstract class ComponentSystem(
    vararg val componentTypes: Class<out Component>
) {

    var componentSignature = 0L
        private set

    // Creates a bit mask containing which component types this system will process
    fun updateComponentSignature(componentRegister: HashMap<Class<out Component>, Int>)
    {
        componentSignature = 0L
        componentTypes.forEach {
            componentRegister[it]?.let { index ->
                componentSignature = componentSignature or (1 shl index).toLong()
            }
        }
    }

    abstract fun update(engine: EngineInterface, entities: EntityCollection)
}