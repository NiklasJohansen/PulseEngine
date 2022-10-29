package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.PulseEngine
import org.joml.Vector2f
import org.joml.Vector3f
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.math.PI

object Extensions
{
    /**
     * Linearly interpolates from the [last] value to [this] value.
     */
    fun Float.interpolateFrom(last: Float, t: Float = PulseEngine.GLOBAL_INSTANCE.data.interpolation): Float =
        this * t + last * (1f - t)

    /**
     * Linearly interpolates from the [last] value to [this] value.
     */
    fun Vector2f.interpolateFrom(
        last: Vector2f,
        dest: Vector2f = Vector2f(),
        t: Float = PulseEngine.GLOBAL_INSTANCE.data.interpolation
    ): Vector2f = dest.set(
        this.x * t + last.x * (1f - t),
        this.y * t + last.y * (1f - t)
    )

    /**
     * Linearly interpolates from the [last] value to [this] value.
     */
    fun Vector3f.interpolateFrom(
        last: Vector3f,
        destination: Vector3f = Vector3f(),
        t: Float = PulseEngine.GLOBAL_INSTANCE.data.interpolation
    ): Vector3f = destination.set(
        this.x * t + last.x * (1f - t),
        this.y * t + last.y * (1f - t),
        this.z * t + last.z * (1f - t)
    )

    /**
     * Converts radians to degrees.
     */
    fun Float.toDegrees() = this / PI.toFloat() * 180f

    /**
     * Converts degrees to radians.
     */
    fun Float.toRadians() = this / 180f * PI.toFloat()

    /**
     * Calculates the degrees between [this] and [angle].
     */
    fun Float.degreesBetween(angle: Float): Float
    {
        val delta = this - angle
        return delta + if (delta > 180) -360 else if (delta < -180) 360 else 0
    }

    /**
     * Fast iteration of a list without needing a new [Iterator] instance.
     */
    inline fun <T> List<T>.forEachFiltered(predicate: (T) -> Boolean, action: (T) -> Unit)
    {
        var i = 0
        while (i < size)
        {
            val element = this[i++]
            if (predicate(element)) action(element)
        }
    }

    /**
     * Sums each value of a [List] if the value satisfies a certain [predicate].
     */
    inline fun <T> List<T>.sumIf(predicate: (T) -> Boolean, selector: (T) -> Float): Float
    {
        var sum = 0f
        var i = 0
        while (i < size)
        {
            val element = this[i++]
            if (predicate(element))
                sum += selector(element)
        }
        return sum
    }

    /**
     * Sums each value of a [List].
     */
    inline fun <T> List<T>.sumByFloat(selector: (T) -> Float): Float
    {
        var sum = 0f
        var i = 0
        while (i < size)
            sum += selector(this[i++])
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
     * Fast iteration of [Array].
     */
    inline fun <T> Array<T>.forEachFast(block: (T) -> Unit)
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
     * Returns the last element matching the given predicate.
     */
    inline fun <T> List<T>.lastOrNullFast(predicate: (T) -> Boolean): T?
    {
        var i = lastIndex
        while (i >= 0)
        {
            val element = this[i--]
            if (predicate(element))
                return element
        }
        return null
    }

    /**
     * Prints the time in milliseconds from 'this' to now.
     * @receiver Start time in nanoseconds.
     */
    fun Long.toNowFormatted(): String = "${"%.3f".format((System.nanoTime() - this) / 1_000_000f)} ms"

    /**
     * Executes the given [block] and returns elapsed time in milliseconds
     */
    inline fun measureMillisTime(block: () -> Unit): Float
    {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000f
    }

    /**
     * Class path resources (inside jar or at build dir) needs a leading forward slash
     */
    fun String.toClassPath(): String = this.takeIf { it.startsWith("/") } ?: "/$this"

    /**
     * Loads the file as a [InputStream] from class path
     */
    fun String.loadStream(): InputStream? =
        Extensions::class.java.getResourceAsStream(this.toClassPath())

    /**
     * Loads the file as a [ByteArray] from class path
     */
    fun String.loadBytes(): ByteArray? = Extensions::class.java.getResource(this.toClassPath())?.readBytes()

    /**
     * Loads the text content from the given file in class path
     */
    fun String.loadText(): String? = Extensions::class.java.getResource(this.toClassPath())?.readText()

    /**
     * Loads all file names in the given directory
     */
    fun String.loadFileNames(): List<String>
    {
        val uri = Extensions::class.java.getResource(this.toClassPath())?.toURI()
        return if (uri?.scheme == "jar")
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