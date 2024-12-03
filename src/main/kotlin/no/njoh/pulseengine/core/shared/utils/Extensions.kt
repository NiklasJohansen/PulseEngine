package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.collections.HashSet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Extensions
{
    /**
     * Linearly interpolates from the [last] value to [this] value.
     */
    fun Float.interpolateFrom(last: Float, t: Float = PulseEngine.GLOBAL_INSTANCE.data.interpolation): Float =
        this * t + last * (1f - t)

    /**
     * Linearly interpolates from [lastAngle] to [this] angle.
     */
    fun Float.interpolateAngleFrom(lastAngle: Float, t: Float = PulseEngine.GLOBAL_INSTANCE.data.interpolation): Float =
        this + this.degreesBetween(lastAngle).interpolateFrom(0f, t)

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
        val aRad = this.toRadians()
        val bRad = angle.toRadians()
        return MathUtil.atan2(sin(aRad - bRad), cos(aRad - bRad)).toDegrees()
    }

    // For destructuring vectors
    operator fun Vector2f.component1() = x
    operator fun Vector2f.component2() = y
    operator fun Vector3f.component1() = x
    operator fun Vector3f.component2() = y
    operator fun Vector3f.component3() = z
    operator fun Vector4f.component1() = x
    operator fun Vector4f.component2() = y
    operator fun Vector4f.component3() = z
    operator fun Vector4f.component4() = w

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
        var i = 0
        val size = size
        var sum = 0f
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
        var i = 0
        val size = size
        var sum = 0f
        while (i < size)
            sum += selector(this[i++])
        return sum
    }

    /**
     * Fast iteration of constant lookup lists.
     */
    inline fun <T> List<T>.forEachFast(action: (T) -> Unit)
    {
        var i = 0
        while (i < size) action(this[i++])
    }

    /**
     * Fast iteration of constant lookup lists with index.
     */
    inline fun <T> List<T>.forEachIndexedFast(action: (Int, T) -> Unit)
    {
        var i = 0
        while (i < size) action(i, this[i++])
    }

    /**
     * Fast iteration of [Array].
     */
    inline fun <T> Array<T>.forEachFast(action: (T) -> Unit)
    {
        var i = 0
        val size = size
        while (i < size) action(this[i++])
    }

    /**
     * Fast iteration of [LongArray].
     */
    inline fun LongArray.forEachFast(action: (Long) -> Unit)
    {
        var i = 0
        val size = size
        while (i < size) action(this[i++])
    }

    /**
     * Fast iteration of [FloatArray].
     */
    inline fun FloatArray.forEachFast(action: (Float) -> Unit)
    {
        var i = 0
        val size = size
        while (i < size) action(this[i++])
    }

    /**
     * Fast and reversed iteration of constant lookup lists.
     */
    inline fun <T> List<T>.forEachReversed(action: (T) -> Unit)
    {
        var i = size - 1
        while (i > -1) action(this[i--])
    }

    /**
     * Fast lookup of the first element matching the given predicate for constant lookup lists.
     */
    inline fun <T> List<T>.firstOrNullFast(predicate: (T) -> Boolean): T?
    {
        var i = 0
        val size = size
        while (i < size)
        {
            val element = this[i++]
            if (predicate(element))
                return element
        }
        return null
    }

    /**
     * Fast lookup of the last element matching the given predicate for constant lookup lists.
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
     * Checks if any of the list elements matches the predicate (fast for constant lookup lists).
     */
    inline fun <T> List<T>.anyMatches(predicate: (T) -> Boolean): Boolean
    {
        var i = 0
        val size = size
        while (i < size)
        {
            if (predicate(this[i++])) return true
        }
        return false
    }

    /**
     * Checks if none of the list elements matches the predicate (fast for constant lookup lists).
     */
    inline fun <T> List<T>.noneMatches(predicate: (T) -> Boolean) = !anyMatches(predicate)

    /**
     * Checks if the value is in the list (fast for constant lookup lists).
     */
    infix fun <T> T.isIn(list: List<T>): Boolean
    {
        var i = 0
        val size = list.size
        while (i < size)
        {
            if (list[i++] == this) return true
        }
        return false
    }

    /**
     * Checks if the value is not in the list (fast for constant lookup lists).
     */
    infix fun <T> T.isNotIn(list: List<T>) = !isIn(list)

    /**
     * Faster removeIf implementation for constant lookup lists.
     * Removes all elements matching the given predicate. Uses a one-pass approach with no memory allocation.
     * Other operations should not be performed on the list during this operation.
     */
    inline fun <T> MutableList<T>.removeWhen(predicate: (T) -> Boolean): Boolean
    {
        // Find first element to remove
        var headIndex = 0
        val size = size
        while (true)
        {
            if (headIndex == size)
                return false
            if (predicate(this[headIndex]))
                break
            headIndex++
        }

        // Skip elements to remove and copy the rest
        var tailIndex = headIndex
        while (++headIndex < size)
        {
            val value = this[headIndex]
            if (!predicate(value))
                this[tailIndex++] = value
        }

        // Null out the rest of the list
        while (this.size > tailIndex) removeLast()

        return true
    }

    /**
     * Maps a list of type [T] to a map of type [R].
     */
    inline fun <T, R> List<T>.mapToSet(transform: (T) -> R): Set<R>
    {
        val destination = HashSet<R>()
        var i = 0
        val size = size
        while (i < size)
        {
            val element = this[i++]
            destination.add(transform(element))
        }
        return destination
    }

    /**
     * Returns a new [LongArray] with the first occurrence of [value] removed.
     * Returns null if the result is empty.
     */
    fun LongArray.minus(value: Long): LongArray?
    {
        val size = size
        if (size == 0 || (size == 1 && this[0] == value))
            return null

        var i = 0
        var j = 0
        val array = LongArray(size - 1)
        while (i < array.size && j < size)
        {
            val v = this[j++]
            if (v == value) continue
            array[i++] = v
        }
        return array
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
    fun String.loadStream(): InputStream? = Extensions::class.java.getResourceAsStream(this.toClassPath())

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

    /**
     * Formats the Float value to a String with a given numbers of decimals.
     */
    fun Float.formatted(decimals: Int = 1) = StringBuilder().append(this, decimals).toString()

    /**
     * Appends the given [value] to the [StringBuilder] with the given number of [decimals].
     */
    fun StringBuilder.append(value: Float, decimals: Int): StringBuilder
    {
        var integral = value.toInt()
        var fraction = value - integral
        append(integral)
        if (decimals > 0)
            append('.')
        for (i in 0 until decimals)
        {
            fraction *= 10f
            integral = fraction.toInt()
            fraction -= integral
            append(integral)
        }
        return this
    }
}