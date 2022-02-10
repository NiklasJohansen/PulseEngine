package no.njoh.pulseengine.modules.graphics.ui

import no.njoh.pulseengine.modules.graphics.ui.Size.ValueType.ABSOLUTE
import no.njoh.pulseengine.modules.graphics.ui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.graphics.ui.layout.VerticalPanel
import no.njoh.pulseengine.modules.shared.utils.Extensions.sumByFloat

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
        is HorizontalPanel -> children.map { it.getRequiredVerticalSpace() }.max() ?: minHeight
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
        is VerticalPanel -> children.map { it.getRequiredHorizontalSpace() }.max() ?: minWidth
        else -> when (width.type)
        {
            ABSOLUTE -> width.value
            else -> minWidth
        }
    }
}