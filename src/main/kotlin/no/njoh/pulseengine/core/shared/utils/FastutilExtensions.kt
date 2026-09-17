package no.njoh.pulseengine.core.shared.utils

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

fun <T> emptyObjectIntHashMap(capacity: Int = 10, noEntryValue: Int = -2) =
    Object2IntOpenHashMap<T>(capacity).also { it.defaultReturnValue(noEntryValue) }

inline fun <K> Object2IntOpenHashMap<K>.getOrPut(key: K, defaultValue: (key: K) -> Int): Int
{
    val value = getInt(key)
    if (value != defaultReturnValue() || containsKey(key))
        return value
    val answer = defaultValue(key)
    put(key, answer)
    return answer
}

inline fun <V> Long2ObjectOpenHashMap<V>.getOrPut(key: Long, defaultValue: (key: Long) -> V): V
{
    val value = get(key)
    if (value != null)
        return value
    val answer = defaultValue(key)
    put(key, answer)
    return answer
}

inline fun <K, V> Object2ObjectOpenHashMap<K, V>.retainEntries(predicate: (K, V) -> Boolean)
{
    val iterator = object2ObjectEntrySet().fastIterator()
    while (iterator.hasNext())
    {
        val entry = iterator.next()
        if (!predicate(entry.key, entry.value)) iterator.remove()
    }
}

inline fun Long2LongOpenHashMap.retainEntries(predicate: (Long, Long) -> Boolean)
{
    val iterator = long2LongEntrySet().fastIterator()
    while (iterator.hasNext())
    {
        val entry = iterator.next()
        if (!predicate(entry.longKey, entry.longValue)) iterator.remove()
    }
}
