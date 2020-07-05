package no.njoh.pulseengine.modules.entity

open class Entity(
    val id: EntityId,
    var alive: Boolean,
    val signature: Long,
    protected val components: Array<Component?>
) {
    inline fun <reified T: Component> getComponent(componentType: ComponentType<T>) : T = components[componentType.index] as T
}