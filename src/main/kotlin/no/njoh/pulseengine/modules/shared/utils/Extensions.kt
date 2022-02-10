package no.njoh.pulseengine.modules.shared.utils

import no.njoh.pulseengine.PulseEngine
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.math.PI

object Extensions
{
    fun Float.interpolateFrom(lastState: Float): Float
    {
        val i = PulseEngine.GLOBAL_INSTANCE.data.interpolation
        return this * i + lastState * (1f - i)
    }

    fun Float.toDegrees() = this / PI.toFloat() * 180f

    fun Float.toRadians() = this / 180f * PI.toFloat()

    fun Float.degreesBetween(angle: Float): Float
    {
        val delta = this - angle
        return delta + if (delta > 180) -360 else if (delta < -180) 360 else 0
    }

    inline fun <T> List<T>.forEachFiltered(predicate: (T) -> Boolean, action: (T) -> Unit)
    {
        var i = 0
        while (i < size)
        {
            val element = this[i++]
            if (predicate(element)) action(element)
        }
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

    /**
     * Fast iteration of constant lookup lists.
     */
    inline fun <T> List<T>.forEachFast(block: (T) -> Unit)
    {
        var i = 0
        while (i < size) block(this[i++])
    }

    /**
     * Fast and reversed iteration of constant lookup lists.
     */
    inline fun <T> List<T>.forEachReversed(block: (T) -> Unit)
    {
        var i = size - 1
        while (i > -1) block(this[i--])
    }

    /**
     * Returns the first element matching the given predicate.
     */
    inline fun <T> List<T>.firstOrNullFast(predicate: (T) -> Boolean): T?
    {
        var i = 0
        while (i < size)
        {
            val element = this[i++]
            if (predicate(element))
                return element
        }
        return null
    }

    /**
     * Class path resources (inside jar or at build dir) needs a leading forward slash
     */
    fun String.toClassPath(): String =
        this.takeIf { it.startsWith("/") } ?: "/$this"

    /**
     * Loads the file as a [InputStream] from class path
     */
    fun String.loadStream(): InputStream? =
        javaClass.getResourceAsStream(this.toClassPath())

    /**
     * Loads the file as a [ByteArray] from class path
     */
    fun String.loadBytes(): ByteArray? =
        javaClass.getResource(this.toClassPath())?.readBytes()

    /**
     * Loads the text content from the given file in class path
     */
    fun String.loadText(): String? =
        javaClass.getResource(this.toClassPath())?.readText()

    /**
     * Loads all file names in the given directory
     */
    fun String.loadFileNames(): List<String>
    {
        val uri = javaClass.getResource(this.toClassPath()).toURI()
        return if (uri.scheme == "jar")
        {
            val fileSystem =
                try { FileSystems.getFileSystem(uri) }
                catch (e: Exception) { FileSystems.newFileSystem(uri, emptyMap<String, Any>()) }
            Files.walk(fileSystem.getPath(this.toClassPath()), 1).iterator()
                .asSequence()
                .map { it.toAbsolutePath().toString() }
                .toList()
        }
        else
        {
            this.loadStream()
                ?.let { stream -> BufferedReader(InputStreamReader(stream)).readLines().map { "$this/$it" } }
                ?: emptyList()
        }
    }
}

/*
fun Float.interpolateFrom(lastState: Float): Float
{
    val i = PulseEngine.GLOBAL_INSTANCE.data.interpolation
    return this * i + lastState * (1f - i)
}

fun Float.toDegrees() = this / PI.toFloat() * 180f

fun Float.toRadians() = this / 180f * PI.toFloat()

fun Float.degreesBetween(angle: Float): Float
{
    val delta = this - angle
    return delta + if (delta > 180) -360 else if (delta < -180) 360 else 0
}

inline fun <T> List<T>.forEachFiltered(predicate: (T) -> Boolean, action: (T) -> Unit)
{
    var i = 0
    while (i < size)
    {
        val element = this[i++]
        if (predicate(element)) action(element)
    }
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

/**
 * Fast iteration of constant lookup lists.
 */
inline fun <T> List<T>.forEachFast(block: (T) -> Unit)
{
    var i = 0
    while (i < size) block(this[i++])
}

/**
 * Fast and reversed iteration of constant lookup lists.
 */
inline fun <T> List<T>.forEachReversed(block: (T) -> Unit)
{
    var i = size - 1
    while (i > -1) block(this[i--])
}

/**
 * Returns the first element matching the given predicate.
 */
inline fun <T> List<T>.firstOrNullFast(predicate: (T) -> Boolean): T?
{
    var i = 0
    while (i < size)
    {
        val element = this[i++]
        if (predicate(element))
            return element
    }
    return null
}

/**
 * Class path resources (inside jar or at build dir) needs a leading forward slash
 */
fun String.toClassPath(): String =
    this.takeIf { it.startsWith("/") } ?: "/$this"

/**
 * Loads the file as a [InputStream] from class path
 */
fun String.loadStream(): InputStream? =
    javaClass.getResourceAsStream(this.toClassPath())

/**
 * Loads the file as a [ByteArray] from class path
 */
fun String.loadBytes(): ByteArray? =
    javaClass.getResource(this.toClassPath())?.readBytes()

/**
 * Loads the text content from the given file in class path
 */
fun String.loadText(): String? =
    javaClass.getResource(this.toClassPath())?.readText()

/**
 * Loads all file names in the given directory
 */
fun String.loadFileNames(): List<String>
{
    val uri = javaClass.getResource(this.toClassPath()).toURI()
    return if (uri.scheme == "jar")
    {
        val fileSystem =
            try { FileSystems.getFileSystem(uri) }
            catch (e: Exception) { FileSystems.newFileSystem(uri, emptyMap<String, Any>()) }
        Files.walk(fileSystem.getPath(this.toClassPath()), 1).iterator()
            .asSequence()
            .map { it.toAbsolutePath().toString() }
            .toList()
    }
    else
    {
        this.loadStream()
            ?.let { stream -> BufferedReader(InputStreamReader(stream)).readLines().map { "$this/$it" } }
            ?: emptyList()
    }
}
*/
