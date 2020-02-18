package engine.modules.entity

open class Entity(
    val id: EntityId,
    var alive: Boolean,
    val signature: Long,
    protected val components: HashMap<Class<out Component>, Component>
) {
    inline fun <reified T: Component> getComponent() : T = components[T::class.java] as T
}