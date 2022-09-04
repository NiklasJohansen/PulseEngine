package no.njoh.pulseengine.modules.gui.layout

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.Surface2D

import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.Size.ValueType.ABSOLUTE
import no.njoh.pulseengine.modules.gui.Size.ValueType.AUTO
import no.njoh.pulseengine.modules.gui.UiUtil.getRequiredHorizontalSpace
import no.njoh.pulseengine.modules.gui.elements.ResizeBarGizmo
import no.njoh.pulseengine.modules.gui.UiElement
import no.njoh.pulseengine.core.shared.utils.Extensions.sumByFloat
import kotlin.math.max
import kotlin.math.min

class HorizontalResizeGizmo(
    private val hPanel: HorizontalPanel
): UiElement(hPanel.x, hPanel.y, hPanel.width, hPanel.height) {

    init { focusable = false }

    override fun updateChildLayout()
    {
        val nBars = hPanel.children.size - 1
        if (children.size != nBars)
        {
            clearChildren()
            for (i in 0 until nBars)
                addChildren(ResizeBarGizmo(isVertical = true, width = Size.absolute(10f)))
        }

        for (i in 0 until nBars)
        {
            val bar = children[i] as ResizeBarGizmo
            val leftChild = hPanel.children[i]
            val rightChild = hPanel.children[i + 1]

            val totalWidth = leftChild.width.value + rightChild.width.value
            var leftMinWidth = max(leftChild.actualMinWidth(), totalWidth - rightChild.maxWidth)
            var leftMaxWidth = min(leftChild.maxWidth, totalWidth - rightChild.actualMinWidth())

            if (leftMinWidth > leftMaxWidth)
            {
                leftMinWidth = (leftMinWidth + leftMaxWidth) / 2f
                leftMaxWidth = leftMinWidth
            }

            if (bar.positionDiff != 0f)
            {
                val leftWidth = leftChild.width.value
                val change = (leftWidth + bar.positionDiff).coerceIn(leftMinWidth, leftMaxWidth) - leftWidth
                leftChild.width.value += change
                rightChild.width.value -= change
                rightChild.x.value += change

                when
                {
                    leftChild.width.type == ABSOLUTE -> rightChild.width.type = AUTO
                    rightChild.width.type == ABSOLUTE -> leftChild.width.type = AUTO
                    leftChild is HorizontalPanel || leftChild is VerticalPanel ->
                    {
                        leftChild.width.type = AUTO
                        rightChild.width.type = ABSOLUTE
                    }
                    rightChild is HorizontalPanel || rightChild is VerticalPanel ->
                    {
                        leftChild.width.type = ABSOLUTE
                        rightChild.width.type = AUTO
                    }
                    else ->
                    {
                        leftChild.width.type = AUTO
                        rightChild.width.type = ABSOLUTE
                    }
                }
            }

            bar.x.setQuiet(rightChild.x.value - bar.width.value / 2f)
            bar.y.setQuiet(rightChild.y.value)
            bar.height.setQuiet(rightChild.height.value)
            bar.positionDiff = 0f
            bar.updateLayout()
            bar.setLayoutClean()
        }
    }

    private fun UiElement.actualMinWidth(): Float = when (this)
    {
        is HorizontalPanel -> children.sumByFloat { it.getRequiredHorizontalSpace() }
        is VerticalPanel -> children.maxOfOrNull { it.getRequiredHorizontalSpace() } ?: minWidth
        else -> minWidth
    }

    override fun onUpdate(engine: PulseEngine) { }
    override fun onRender(engine: PulseEngine, surface: Surface2D) { }
}