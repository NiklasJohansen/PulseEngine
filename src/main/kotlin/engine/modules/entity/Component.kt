package engine.modules.entity

abstract class Component

class ComponentType<T: Component> (
    val type: Class<T>
) {
    val index = count++

    fun getInstance(): T =  type.getConstructor().newInstance()

    companion object
    {
        var count = 0
            private set
    }
}