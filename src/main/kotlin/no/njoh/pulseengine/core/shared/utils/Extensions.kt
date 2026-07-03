package no.njoh.pulseengine.core.shared.utils

import gnu.trove.list.array.TIntArrayList
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.*
import org.joml.Vector2f
import org.joml.Vector2i
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.charset.Charset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object Extensions
{
    /**
     * Linearly interpolates from the [last] value to [this] value.
     */
    fun Float.interpolateFrom(last: Float, t: Float = PulseEngine.INSTANCE.data.interpolation): Float =
        this * t + last * (1f - t)

    /**
     * Linearly interpolates from [lastAngle] to [this] angle.
     */
    fun Float.interpolateAngleFrom(lastAngle: Float, t: Float = PulseEngine.INSTANCE.data.interpolation): Float =
        this + this.degreesBetween(lastAngle).interpolateFrom(0f, t)

    /**
     * Linearly interpolates from the [last] value to [this] value.
     */
    fun Vector2f.interpolateFrom(
        last: Vector2f,
        dest: Vector2f = Vector2f(),
        t: Float = PulseEngine.INSTANCE.data.interpolation
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
        t: Float = PulseEngine.INSTANCE.data.interpolation
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

    /**
     * Converts a color value from sRGB to linear space.
     */
    fun Float.srgbToLinear(): Float = when
    {
        !isFinite() -> 0f
        this <= 0.04045f -> this / 12.92f
        else -> ((this + 0.055f) / 1.055f).pow(2.4f)
    }

    /**
     * Converts a color value from linear space to sRGB.
     */
    fun Float.linearToSrgb(): Float = when
    {
        !isFinite() -> 0f
        this <= 0.0031308f -> this * 12.92f
        else -> 1.055f * this.pow(1f / 2.4f) - 0.055f
    }

    // For destructuring vectors
    operator fun Vector2i.component1() = x
    operator fun Vector2i.component2() = y
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
     * Fast inline iteration of constant lookup lists without any allocations.
     */
    inline fun <T> List<T>.forEachFast(action: (T) -> Unit)
    {
        var i = 0
        while (i < size) action(this[i++])
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
     * Fast iteration of a list without needing a new [Iterator] instance.
     */
    inline fun <reified T> List<*>.forEachInstance(action: (T) -> Unit)
    {
        var i = 0
        while (i < size)
        {
            val element = this[i++]
            if (element is T) action(element)
        }
    }

    /**
     * Fast inline iteration of TIntArrayList without any allocations.
     */
    inline fun TIntArrayList.forEachFast(action: (Int) -> Unit)
    {
        var i = 0
        val size = size()
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
    * Adds all elements of the [other] list to this list without creating an iterator or new array.
    */
    fun <T> MutableList<T>.addAllFast(other: List<T>)
    {
        var i = 0
        val size = other.size
        while (i < size) add(other[i++])
    }

    /**
     * Sorts the list in-place using the quicksort algorithm and the given [comparator].
     */
    fun <T> MutableList<T>.quickSort(comparator: Comparator<T>)
    {
        if (size > 1) this.quickSort(0, size - 1, comparator)
    }

    /**
     * Sorts the list in-place using the quicksort algorithm and the given [comparator].
     * This does not allocate any memory.
     */
    internal fun <T> MutableList<T>.quickSort(left: Int, right: Int, comparator: Comparator<T>)
    {
        var i = left
        var j = right
        val pivot = this[(left + right) ushr 1]

        while (i <= j)
        {
            while (comparator.compare(this[i], pivot) < 0) i++
            while (comparator.compare(this[j], pivot) > 0) j--

            if (i <= j)
            {
                if (i != j)
                {
                    val tmp = this[i]
                    this[i] = this[j]
                    this[j] = tmp
                }
                i++
                j--
            }
        }

        if (left < j) quickSort(left, j, comparator)
        if (i < right) quickSort(i, right, comparator)
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
    fun Long.toNowFormatted(): String = "${"%.3f".format((System.nanoTime() - this).toDouble() * 1e-6)} ms"

    /**
     * Executes the given [block] and returns elapsed time in milliseconds
     */
    inline fun measureMillisTime(block: () -> Unit): Float
    {
        val start = System.nanoTime()
        block()
        return ((System.nanoTime() - start).toDouble() * 1e-6).toFloat()
    }

    /**
     * Reads this text file from besides the JAR or from inside it.
     * Example: "directory/file.txt".loadTextFromPath().
     */
    fun String.loadTextFromPath(charset: Charset = Charsets.UTF_8) = 
        ResourceResolver.open(this)?.bufferedReader(charset)?.use { it.readText() }

    /**
     * Reads this file as bytes from besides the JAR or from inside it.
     * Example: "directory/file.ext".loadBytesFromPath().
     */
    fun String.loadBytesFromPath() = ResourceResolver.open(this)?.use { it.readBytes() }

    /**
     * Reads only a packaged resource, ignoring a matching file outside the JAR.
     * Example: "defaults/file.ext".loadBytesFromClassPath().
     */
    fun String.loadBytesFromClassPath() = ResourceResolver.openClasspath(this)?.use { it.readBytes() }

    /**
     * Lists files directly inside this directory and files outside the JAR.
     * Example: "directory".loadFileNames() may return ["directory/file-a.ext", "directory/file-b.ext"].
     */
    fun String.loadFileNames(): List<String> = ResourceResolver.listFiles(this)

    private val spriteSheetRegex = "_([0-9]{1,3})x([0-9]{1,3})\\.".toRegex() // Matches _1x2.

    /**
     * Default function for creating assets from file paths.
     */
    fun pathToAsset(path: String): Asset?
    {
        val name = path.substringAfterLast("\\").substringAfterLast("/").substringBeforeLast(".")
        return when
        {
            path.endsWith(".ogg")  -> Sound(path, name)
            path.endsWith(".ttf")  -> Font(path, name)
            path.endsWith(".txt")  -> Text(path, name)
            path.endsWith(".dat")  -> Binary(path, name)
            path.endsWith(".hdr")  -> EnvMap(path, name)
            path.endsWith(".obj")  ||
            path.endsWith(".fbx")  ||
            path.endsWith(".glb")  ||
            path.endsWith(".gltf") -> Model(path, name)
            path.endsWith(".jpg")  ||
            path.endsWith(".jpeg") ||
            path.endsWith(".png")  ->
            {
                val isLut = "_lut" in name
                val isPBR = "_normal" in name || "_ao" in name || "_metallic" in name || "_roughness" in name
                val format = if (isLut || isPBR || "_linear" in name) RGBA8 else SRGBA8

                spriteSheetRegex.find(path)?.let { SpriteSheet(
                    filePath = path,
                    name = name.substringBeforeLast("_"),
                    format = format,
                    horizontalCells = it.groupValues[1].toInt(),
                    verticalCells = it.groupValues[2].toInt()
                ) } ?: Texture(path, name, format = format, wrapping = CLAMP_TO_EDGE)
            }
            else -> null
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
