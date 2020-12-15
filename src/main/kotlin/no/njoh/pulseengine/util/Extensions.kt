package no.njoh.pulseengine.util

import no.njoh.pulseengine.PulseEngine

fun Float.interpolateFrom(lastState: Float): Float
{
    val i = PulseEngine.GLOBAL_INSTANCE.data.interpolation
    return this * i + lastState * (1f - i)
}

inline fun <T> List<T>.forEachFiltered(predicate: (T) -> Boolean, action: (T) -> Unit)
{
    for (element in this)
        if (predicate.invoke(element))
            action(element)
}

inline fun <T> Iterable<T>.sumIf(predicate: (T) -> Boolean, selector: (T) -> Float): Float
{
    var sum = 0f
    for (element in this)
        if (predicate(element))
            sum += selector(element)
    return sum
}

inline fun <T> Iterable<T>.sumByFloat(selector: (T) -> Float): Float
{
    var sum = 0f
    for (element in this)
        sum += selector(element)
    return sum
}