package no.njoh.pulseengine.modules.gui

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.modules.gui.Size.ValueType.ABSOLUTE
import no.njoh.pulseengine.modules.gui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.gui.layout.VerticalPanel
import no.njoh.pulseengine.core.shared.utils.Extensions.sumByFloat

@Suppress("UNCHECKED_CAST")
object UiUtils
{
    /**
     * Returns the first [UiElement] with a matching id among its children or itself.
     */
    fun UiElement.findElement(id: String): UiElement?
    {
        if (this.id == id) return this

        popup?.findElement(id)?.let { return it }
        children.forEachFast { child -> child.findElement(id)?.let { return it } }

        return null
    }

    /**
     * Returns the first [UiElement] with a matching type [T] among its children or itself.
     */
    inline fun <reified T> UiElement.findElement() = findElement(T::class.java)

    /**
     * Returns the first [UiElement] with a matching [type] among its children or itself.
     */
    fun <T> UiElement.findElement(type: Class<T>): T?
    {
        if (type.isAssignableFrom(this::class.java)) return this as T

        popup?.findElement(type)?.let { return it }
        children.forEachFast { child -> child.findElement(type)?.let { return it } }

        return null
    }

    /**
     * Returns the first [UiElement] with a matching [id] and type [T] among its children or itself.
     */
    inline fun <reified T> UiElement.findElement(id: String) = findElement(id, T::class.java)

    /**
     * Returns the first [UiElement] with a matching [id] and [type] among its children or itself.
     */
    fun <T> UiElement.findElement(id: String, type: Class<T>): T?
    {
        if (this.id == id && type.isAssignableFrom(this::class.java)) return this as T

        popup?.findElement(type)?.let { return it }
        children.forEachFast { child -> child.findElement(type)?.let { return it } }

        return null
    }

    /**
     * Returns the first [UiElement] satisfying the given [predicate] among its children or it self, else null.
     */
    fun UiElement.firstElementOrNull(predicate: (UiElement) -> Boolean): UiElement?
    {
        if (predicate(this)) return this

        popup?.firstElementOrNull(predicate)?.let { return it }
        children.forEachFast { child -> child.firstElementOrNull(predicate)?.let { return it } }

        return null
    }

    /**
     * Calculates the required vertical space of it children or it self.
     */
    fun UiElement.getRequiredVerticalSpace(): Float = when(this)
    {
        is VerticalPanel -> children.sumByFloat { it.getRequiredVerticalSpace() }
        is HorizontalPanel -> children.maxOfOrNull { it.getRequiredVerticalSpace() } ?: minHeight.value
        else -> when (height.type)
        {
            ABSOLUTE -> height.value
            else -> minHeight.value
        }
    }

    /**
     * Calculates the required horizontal space of it children or it self.
     */
    fun UiElement.getRequiredHorizontalSpace(): Float = when(this)
    {
        is HorizontalPanel -> children.sumByFloat { it.getRequiredHorizontalSpace() }
        is VerticalPanel -> children.maxOfOrNull { it.getRequiredHorizontalSpace() } ?: minWidth.value
        else -> when (width.type)
        {
            ABSOLUTE -> width.value
            else -> minWidth.value
        }
    }

    /**
     * Returns true if the [UiElement] itself, its popup or any of its children has focus.
     */
    fun UiElement.hasFocus(engine: PulseEngine): Boolean =
        engine.input.hasFocus(this.area) ||
        popup?.hasFocus(engine) ?: false ||
        children.anyMatches { it.hasFocus(engine) }
}