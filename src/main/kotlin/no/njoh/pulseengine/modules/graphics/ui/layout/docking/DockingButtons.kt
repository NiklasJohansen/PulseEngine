package no.njoh.pulseengine.modules.graphics.ui.layout.docking

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.UiElement
import no.njoh.pulseengine.modules.graphics.ui.layout.docking.DockingButtons.CenterLine.*

class DockingButtons : UiElement(Position.auto(), Position.auto(), Size.auto(), Size.auto())
{
    var centerHover = false
    var leftHover = false
    var rightHover = false
    var topHover = false
    var bottomHover = false
    var leftEdgeHover = false
    var rightEdgeHover = false
    var topEdgeHover = false
    var bottomEdgeHover = false

    val size = 60f
    var showCenterButton = false
    var targetPanel: UiElement? = null

    override fun onUpdate(engine: PulseEngine)
    {
        targetPanel?.let {
            updatePanelButtons(engine, it.x.value, it.y.value, it.width.value, it.height.value)
        }

        updateEdgeButtons(engine, x.value, y.value, width.value, height.value)
    }

    override fun onRender(surface: Surface2D)
    {
        targetPanel?.let {
            renderPanelButtons(surface, it.x.value, it.y.value, it.width.value, it.height.value)
        }

        renderEdgeButtons(surface, x.value, y.value, width.value, height.value)
    }

    private fun renderPanelButtons(surface: Surface2D, x: Float, y: Float, parentWidth: Float, parentHeight: Float)
    {
        val xCenter = x + parentWidth / 2f
        val yCenter = y + parentHeight / 2f

        if (showCenterButton)
            surface.drawButton( xCenter - size * 0.5f, yCenter - size * 0.5f, size, size, centerHover, NONE)

        surface.drawButton( xCenter - size * 1.25f, yCenter - size *  0.5f, size * 0.5f, size, leftHover, VERTICAL)
        surface.drawButton( xCenter + size * 0.75f, yCenter - size *  0.5f, size * 0.5f, size, rightHover, VERTICAL)
        surface.drawButton( xCenter - size *  0.5f, yCenter - size * 1.25f, size, size * 0.5f, topHover, HORIZONTAL)
        surface.drawButton( xCenter - size *  0.5f, yCenter + size * 0.75f, size, size * 0.5f, bottomHover, HORIZONTAL)
    }

    private fun renderEdgeButtons(surface: Surface2D, x: Float, y: Float, parentWidth: Float, parentHeight: Float)
    {
        val xCenter = x + parentWidth / 2f
        val yCenter = y + parentHeight / 2f
        surface.drawButton( x + 1, yCenter - size *  0.5f, size * 0.5f, size, leftEdgeHover, VERTICAL)
        surface.drawButton( x + parentWidth - size * 0.5f - 1, yCenter - size *  0.5f, size * 0.5f, size, rightEdgeHover, VERTICAL)
        surface.drawButton( xCenter - size *  0.5f, y + 1, size, size * 0.5f, topEdgeHover, HORIZONTAL)
        surface.drawButton( xCenter - size *  0.5f, y + parentHeight - size * 0.5f - 1, size, size * 0.5f, bottomEdgeHover, HORIZONTAL)
    }

    private fun updatePanelButtons(engine: PulseEngine, x: Float, y: Float, parentWidth: Float, parentHeight: Float)
    {
        val xCenter = x + parentWidth / 2f
        val yCenter = y + parentHeight / 2f
        val xMouse = engine.input.xMouse
        val yMouse = engine.input.yMouse

        centerHover = showCenterButton && inside(xMouse, yMouse, xCenter - size *  0.5f, yCenter - size *  0.5f, size, size)
        leftHover   = inside(xMouse, yMouse, xCenter - size * 1.25f, yCenter - size *  0.5f, size * 0.5f, size)
        rightHover  = inside(xMouse, yMouse, xCenter + size * 0.75f, yCenter - size *  0.5f, size * 0.5f, size)
        topHover    = inside(xMouse, yMouse, xCenter - size *  0.5f, yCenter - size * 1.25f, size, size * 0.5f)
        bottomHover = inside(xMouse, yMouse, xCenter - size *  0.5f, yCenter + size * 0.75f, size, size * 0.5f)
    }

    private fun updateEdgeButtons(engine: PulseEngine, x: Float, y: Float, parentWidth: Float, parentHeight: Float)
    {
        val xCenter = x + parentWidth / 2f
        val yCenter = y + parentHeight / 2f
        val xMouse = engine.input.xMouse
        val yMouse = engine.input.yMouse

        leftEdgeHover   = inside(xMouse, yMouse, x, yCenter - size *  0.5f, size * 0.5f, size)
        rightEdgeHover  = inside(xMouse, yMouse, x + parentWidth - size * 0.5f, yCenter - size *  0.5f, size * 0.5f, size)
        topEdgeHover    = inside(xMouse, yMouse, xCenter - size *  0.5f, y, size, size * 0.5f)
        bottomEdgeHover = inside(xMouse, yMouse, xCenter - size *  0.5f, y + parentHeight - size * 0.5f, size, size * 0.5f)
    }

    private fun Surface2D.drawButton(x: Float, y: Float, width: Float, height: Float, isHovering: Boolean, centerLine: CenterLine)
    {
        setDrawColor(1f, 1f, 1f, if (isHovering) 0.3f * 1.2f else 0.3f)
        drawQuad( x, y, width, height)

        setDrawColor(1f, 1f, 1f, if (isHovering) 0.8f * 1.2f else 0.8f)
        drawLine(x, y, x + width, y)
        drawLine(x, y, x, y + height)
        drawLine(x + width, y, x + width, y + height)
        drawLine(x, y + height, x + width, y + height)
        when (centerLine)
        {
            NONE -> { }
            VERTICAL -> drawLine(x + width / 2f, y + 1, x + width / 2f, y + height - 1)
            HORIZONTAL -> drawLine(x + 1, y + height / 2f, x + width - 1, y + height / 2f)
        }
    }

    private fun inside(x: Float, y: Float, xRect: Float, yRect: Float, wRect: Float, hRect: Float): Boolean =
        x >= xRect && x <= xRect + wRect && y >= yRect && y <= yRect + hRect

    private enum class CenterLine { NONE, VERTICAL, HORIZONTAL }
}