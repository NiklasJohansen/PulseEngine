package engine.modules.entity

abstract class Component

class ComponentType<T: Component> (
    val type: Class<T>
) {
    val id = ComponentID.values()[idCount++]

    fun getInstance(): T =  type.getConstructor().newInstance()

    companion object
    {
        private var idCount = 0
    }
}

enum class ComponentID
{
    COMP_1A, COMP_1B, COMP_1C, COMP_1D, COMP_1E, COMP_1F, COMP_1G, COMP_1H, COMP_1I, COMP_1J, COMP_1K
}
