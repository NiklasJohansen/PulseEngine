package no.njoh.pulseengine.core.graphics.ui.layout

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.Surface2D

import no.njoh.pulseengine.core.graphics.ui.Size
import no.njoh.pulseengine.core.graphics.ui.Size.ValueType.ABSOLUTE
import no.njoh.pulseengine.core.graphics.ui.Size.ValueType.AUTO
import no.njoh.pulseengine.core.graphics.ui.UiUtil.getRequiredVerticalSpace
import no.njoh.pulseengine.core.graphics.ui.elements.ResizeBarGizmo
import no.njoh.pulseengine.core.graphics.ui.UiElement
import no.njoh.pulseengine.core.shared.utils.Extensions.sumByFloat
import kotlin.math.max
import kotlin.math.min

class VerticalResizeGizmo(
    private val vPanel: VerticalPanel
): UiElement(vPanel.x, vPanel.y, vPanel.width, vPanel.height) {

    init { focusable = false }

    override fun updateChildLayout()
    {
        val nBars = vPanel.children.size - 1
        if (children.size != nBars)
        {
            clearChildren()
            for (i in 0 until nBars)
                addChildren(ResizeBarGizmo(isVertical = false, height = Size.absolute(10f)))
        }

        for (i in 0 until nBars)
        {
            val bar = children[i] as ResizeBarGizmo
            val topChild = vPanel.children[i]
            val bottomChild = vPanel.children[i + 1]

            val totalHeight = topChild.height.value + bottomChild.height.value
            var topMinHeight = max(topChild.actualMinHeight(), totalHeight - bottomChild.maxHeight)
            var topMaxHeight = min(topChild.maxHeight, totalHeight - bottomChild.actualMinHeight())

            if (topMinHeight > topMaxHeight)
            {
                topMinHeight = (topMinHeight + topMaxHeight) / 2f
                topMaxHeight = topMinHeight
            }

            if (bar.positionDiff != 0f)
            {
                val topHeight = topChild.height.value
                val change = (topHeight + bar.positionDiff).coerceIn(topMinHeight, topMaxHeight) - topHeight
                topChild.height.value += change
                bottomChild.height.value -= change
                bottomChild.y.value += change

                when
                {
                    topChild.height.type == ABSOLUTE -> bottomChild.height.type = AUTO
                    bottomChild.height.type == ABSOLUTE -> topChild.height.type = AUTO
                    topChild is HorizontalPanel || topChild is VerticalPanel ->
                    {
                        topChild.height.type = AUTO
                        bottomChild.height.type = ABSOLUTE
                    }
                    bottomChild is HorizontalPanel || bottomChild is VerticalPanel ->
                    {
                        topChild.height.type = ABSOLUTE
                        bottomChild.height.type = AUTO
                    }
                    else ->
                    {
                        topChild.height.type = AUTO
                        bottomChild.height.type = ABSOLUTE
                    }
                }
            }

            bar.x.setQuiet(bottomChild.x.value)
            bar.y.setQuiet(bottomChild.y.value - bar.height.value / 2f)
            bar.width.setQuiet(bottomChild.width.value)
            bar.positionDiff = 0f
            bar.updateLayout()
            bar.setLayoutClean()
        }
    }

    private fun UiElement.actualMinHeight(): Float = when (this)
    {
        is VerticalPanel -> children.sumByFloat { it.getRequiredVerticalSpace() }
        is HorizontalPanel -> children.map { it.getRequiredVerticalSpace() }.max() ?: minHeight
        else -> minHeight
    }

    override fun onUpdate(engine: PulseEngine) { }
    override fun onRender(surface: Surface2D) { }
}