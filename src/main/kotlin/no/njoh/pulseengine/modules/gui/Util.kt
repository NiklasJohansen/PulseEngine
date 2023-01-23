package no.njoh.pulseengine.modules.gui

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.modules.gui.Size.ValueType.ABSOLUTE
import no.njoh.pulseengine.modules.gui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.gui.layout.VerticalPanel
import no.njoh.pulseengine.core.shared.utils.Extensions.sumByFloat

object UiUtil
{
    /**
     * Returns the first [UiElement] with a matching id among its children or it self.
     */
    fun UiElement.findElementById(id: String): UiElement?
    {
        if (this.id == id)
            return this

        children.forEachFast { child -> child.findElementById(id)?.let { return it } }

        return null
    }

    /**
     * Returns the first [UiElement] satisfying the given [predicate] among its children or it self, else null.
     */
    fun UiElement.firstElementOrNull(predicate: (UiElement) -> Boolean): UiElement?
    {
        if (predicate(this))
            return this

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
        children.any { it.hasFocus(engine) }
}