package no.njoh.pulseengine.modules.gui

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
        else for (child in this.children)
            child.findElementById(id)?.let { return it }

        return null
    }

    /**
     * Calculates the required vertical space of it children or it self.
     */
    fun UiElement.getRequiredVerticalSpace(): Float = when(this)
    {
        is VerticalPanel -> children.sumByFloat { it.getRequiredVerticalSpace() }
        is HorizontalPanel -> children.maxOfOrNull { it.getRequiredVerticalSpace() } ?: minHeight
        is HorizontalPanel -> children.maxOfOrNull { it.getRequiredVerticalSpace() } ?: minHeight
        else -> when (height.type)
        {
            ABSOLUTE -> height.value
            else -> minHeight
        }
    }

    /**
     * Calculates the required horizontal space of it children or it self.
     */
    fun UiElement.getRequiredHorizontalSpace(): Float = when(this)
    {
        is HorizontalPanel -> children.sumByFloat { it.getRequiredHorizontalSpace() }
        is VerticalPanel -> children.maxOfOrNull { it.getRequiredHorizontalSpace() } ?: minWidth
        else -> when (width.type)
        {
            ABSOLUTE -> width.value
            else -> minWidth
        }
    }
}