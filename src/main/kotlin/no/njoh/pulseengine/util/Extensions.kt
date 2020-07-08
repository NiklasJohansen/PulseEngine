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
