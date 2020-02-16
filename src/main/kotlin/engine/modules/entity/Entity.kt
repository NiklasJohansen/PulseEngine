package engine.modules.entity

// TODO: Create subclasses to optimize for single/double component cases

open class Entity(
    val id: EntityId,
    var alive: Boolean,
    val signature: Long,
    protected val components: HashMap<Class<out Component>, Component>
) {
    inline fun <reified T: Component> getComponent() : T = components[T::class.java] as T
}