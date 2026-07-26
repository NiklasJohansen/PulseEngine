package no.njoh.pulseengine.modules.ui.layout

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Border
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.CornerRadius
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.modules.ui.*

open class Panel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var color = Color.BLANK
    var strokeColor: Color? = null
    var strokeWidth = ScaledValue.of(1f)
    var strokeTop = true
    var strokeBottom = true
    var strokeLeft = true
    var strokeRight = true
    var texture: Texture? = null

    var cornerRadiusTopLeft     = ScaledValue.of(0f)
    var cornerRadiusTopRight    = ScaledValue.of(0f)
    var cornerRadiusBottomRight = ScaledValue.of(0f)
    var cornerRadiusBottomLeft  = ScaledValue.of(0f)

    override fun onUpdate(engine: PulseEngine) { }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        val currentStrokeColor = strokeColor
        val hasStroke = currentStrokeColor != null && currentStrokeColor.alpha != 0f
        val strokeAllEdges = hasStroke && strokeTop && strokeBottom && strokeLeft && strokeRight

        if (color.alpha != 0f || strokeAllEdges)
        {
            surface.setDrawColor(color)
            surface.drawTexture(
                texture = texture ?: Texture.BLANK,
                x = x.value,
                y = y.value,
                width = width.value,
                height = height.value,
                cornerRadius = getCornerRadius(),
                border = if (strokeAllEdges) Border(currentStrokeColor, strokeWidth.value) else Border.ZERO
            )
        }

        if (hasStroke && !strokeAllEdges)
        {
            surface.setDrawColor(currentStrokeColor)
            val verticalEdgeWidth = strokeWidth.value.coerceIn(0f, width.value)
            val horizontalEdgeHeight = strokeWidth.value.coerceIn(0f, height.value)

            if (strokeTop && horizontalEdgeHeight > 0f)
                surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, horizontalEdgeHeight)
            if (strokeLeft && verticalEdgeWidth > 0f)
                surface.drawTexture(Texture.BLANK, x.value, y.value, verticalEdgeWidth, height.value)
            if (strokeRight && verticalEdgeWidth > 0f)
                surface.drawTexture(Texture.BLANK, x.value + width.value - verticalEdgeWidth, y.value, verticalEdgeWidth, height.value)
            if (strokeBottom && horizontalEdgeHeight > 0f)
                surface.drawTexture(Texture.BLANK, x.value, y.value + height.value - horizontalEdgeHeight, width.value, horizontalEdgeHeight)
        }
    }

    fun setCornerRadius(value: ScaledValue)
    {
        cornerRadiusTopLeft     = value
        cornerRadiusTopRight    = value
        cornerRadiusBottomRight = value
        cornerRadiusBottomLeft  = value
    }

    fun getCornerRadius() = CornerRadius(
        cornerRadiusTopLeft.value,
        cornerRadiusTopRight.value,
        cornerRadiusBottomRight.value,
        cornerRadiusBottomLeft.value
    )
}