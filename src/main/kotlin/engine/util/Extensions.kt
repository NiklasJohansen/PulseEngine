package engine.util

import engine.modules.DataInterface

fun Float.interpolateFrom(lastState: Float): Float
{
    val i = DataInterface.INSTANCE.interpolation
    return this * i + lastState * (1f - i)
}

inline fun <T> List<T>.forEachFiltered(predicate: (T) -> Boolean, action: (T) -> Unit)
{
    for(element in this)
        if (predicate.invoke(element))
            action(element)
}
