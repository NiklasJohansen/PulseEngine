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

/////////////////////////////////////////////// BUILT IN COMPONENTS ///////////////////////////////////////////////

class Transform2D : Component()
{
    var x: Float = 0f
    var y: Float = 0f
    companion object { val type = ComponentType(Transform2D::class.java) }
}

