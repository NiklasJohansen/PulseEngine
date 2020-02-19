package engine.modules.entity

import java.util.*

open class Entity(
    val id: EntityId,
    var alive: Boolean,
    val signature: Long,
    protected val components: EnumMap<ComponentID, Component>
) {
    inline fun <reified T: Component> getComponent(componentType: ComponentType<T>) : T = components[componentType.id] as T
}