package no.njoh.pulseengine.core.shared.utils

import gnu.trove.map.hash.TIntObjectHashMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.map.hash.TObjectIntHashMap

/** Default return value from Trove hash maps when no entry was found */
const val TROVE_NO_ENTRY = -2

/**
 * Creates a new [TObjectIntHashMap] with the given [capacity].
 */
inline fun <reified T> emptyObjectIntHashMap(capacity: Int = 10, noEntryValue: Int = TROVE_NO_ENTRY) =
    TObjectIntHashMap<T>(capacity, 0.5f, noEntryValue)

/**
 * Gets the element associated with the given key, or inserts and returns the result of the [defaultValue] function.
 */
inline fun <K> TObjectIntHashMap<K>.getOrPut(key: K, noEntryValue: Int = TROVE_NO_ENTRY, defaultValue: (key: K) -> Int): Int
{
    val value = get(key)
    if (value != noEntryValue)
        return value
    val answer = defaultValue(key)
    put(key, answer)
    return answer
}

/**
 * Gets the element associated with the given key, or inserts and returns the result of the [defaultValue] function.
 */
inline fun <V> TLongObjectHashMap<V>.getOrPut(key: Long, defaultValue: (key: Long) -> V): V
{
    val value = get(key)
    if (value != null)
        return value
    val answer = defaultValue(key)
    put(key, answer)
    return answer
}

operator fun <T> TIntObjectHashMap<T>.get(key: Int): T = get(key)

operator fun <T> TIntObjectHashMap<T>.set(key: Int, value: T) { put(key, value) }