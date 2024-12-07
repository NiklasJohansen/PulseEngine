package no.njoh.pulseengine.core.shared.utils

/**
 * Text builder lambda used create concatenated strings without excessive memory allocations.
 */
typealias TextBuilder = TextBuilderContext.() -> Any

/**
 * Utility class for building concatenated strings without allocating memory for new String objects.
 * Usage:
 *   val context = object : AbstractTextBuilderContext() {} // Create a context once and reuse it
 *   val text = context.build { "Example value:" plus valueVariable plus " unit" }
 */
open class TextBuilderContext
{
    val content = StringBuilder(100)

    infix fun String.plus(s: String): StringBuilder   = content.append(this).append(s)
    infix fun String.plus(c: Char): StringBuilder     = content.append(this).append(c)
    infix fun String.plus(b: Int): StringBuilder      = content.append(this).append(b)
    infix fun String.plus(f: Float): StringBuilder    = content.append(this).append(f)
    infix fun String.plus(l: Long): StringBuilder     = content.append(this).append(l)
    infix fun String.plus(b: Boolean): StringBuilder  = content.append(this).append(b)

    infix fun StringBuilder.plus(s: String): StringBuilder  = if (this !== content) content.append(this).append(s) else append(s)
    infix fun StringBuilder.plus(c: Char): StringBuilder    = if (this !== content) content.append(this).append(c) else append(c)
    infix fun StringBuilder.plus(b: Int): StringBuilder     = if (this !== content) content.append(this).append(b) else append(b)
    infix fun StringBuilder.plus(f: Float): StringBuilder   = if (this !== content) content.append(this).append(f) else append(f)
    infix fun StringBuilder.plus(l: Long): StringBuilder    = if (this !== content) content.append(this).append(l) else append(l)
    infix fun StringBuilder.plus(b: Boolean): StringBuilder = if (this !== content) content.append(this).append(b) else append(b)

    inline fun build(builder: TextBuilder): CharSequence
    {
        val content = content.clear()
        val result = builder(this)
        return when (result)
        {
            is CharSequence -> result
            is Int -> content.append(result)
            is Float -> content.append(result)
            is Long -> content.append(result)
            is Boolean -> content.append(result)
            else -> content.append(result.toString())
        }
    }
}

