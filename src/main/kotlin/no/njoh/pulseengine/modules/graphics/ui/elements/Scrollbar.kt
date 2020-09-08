package no.njoh.pulseengine.modules.graphics.ui.elements

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Scrollable
import no.njoh.pulseengine.modules.graphics.ui.Size
import kotlin.math.max

class Scrollbar(
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(Position.auto(), Position.auto(), width, height) {

    var sliderColor = Color(1f, 1f, 1f)
    var sliderColorHover = Color(1f, 1f, 1f)
    var bgColor = Color(1f, 1f, 1f, 0f)
    var sliderLengthFraction = 0.1f
    var sliderPadding = 0f
    var sliderFraction = 0f
        private set

    private var boundScrollable: Scrollable? = null
    private var onScrollCallback: (Float) -> Unit = { }
    private var isVerticalSlider = false
    private var isMouseOverSlider = false
    private var sliderGrabbed = false
    private var sliderHeight = 0f
    private var sliderWidth = 0f
    private var xSlider = 0f
    private var ySlider = 0f

    override fun onUpdate(engine: PulseEngine)
    {
        val xMouse = engine.input.xMouse
        val yMouse = engine.input.yMouse

        isVerticalSlider = height.value >= width.value
        isMouseOverSlider = (xMouse >= xSlider && xMouse < xSlider + sliderWidth && yMouse >= ySlider && yMouse < ySlider + sliderHeight)
        boundScrollable?.let { sliderLengthFraction = (1f / max(0.0000001f, it.getUsedSpaceFraction())).coerceIn(0.2f, 1f)  }

        if (engine.input.isPressed(Mouse.LEFT))
        {
            if (isMouseOverSlider)
                sliderGrabbed = true
        }
        else sliderGrabbed = false

        if (isVerticalSlider)
        {
            sliderHeight = height.value * sliderLengthFraction - 2f * sliderPadding
            sliderWidth = width.value - 2f * sliderPadding

            val sliderTravelDist = (height.value - sliderHeight - 2f * sliderPadding)

            xSlider = x.value + sliderPadding
            ySlider = y.value + sliderPadding + sliderFraction * sliderTravelDist

            updateSliderFraction(engine.input.ydMouse, engine.input.scroll, sliderTravelDist)
        }
        else
        {
            sliderHeight = height.value - 2f * sliderPadding
            sliderWidth = width.value * sliderLengthFraction - 2f * sliderPadding

            val sliderTravelDist = (width.value - sliderWidth - 2f * sliderPadding)

            ySlider = y.value + sliderPadding
            xSlider = x.value + sliderPadding + sliderFraction * sliderTravelDist

            updateSliderFraction(engine.input.ydMouse, engine.input.scroll, sliderTravelDist)
        }
    }

    private fun updateSliderFraction(mouseChange: Float, scroll: Int, sliderTravelDist: Float)
    {
        if (sliderTravelDist > 0f)
        {
            val scrollChange = -scroll * (sliderTravelDist * 0.2f)
            val fractionChange = (mouseChange + scrollChange) / sliderTravelDist

            if (fractionChange != 0f && (sliderGrabbed || scroll != 0))
            {
                sliderFraction = (sliderFraction + fractionChange).coerceIn(0f, 1f)
                onScrollCallback(sliderFraction)
                boundScrollable?.setScroll(sliderFraction)
            }
        }
    }

    override fun onRender(surface: Surface2D)
    {
        surface.setDrawColor(bgColor.red, bgColor.green, bgColor.blue, bgColor.alpha)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value)

        val sliderColor = if (isMouseOverSlider || sliderGrabbed) sliderColorHover else sliderColor
        surface.setDrawColor(sliderColor.red, sliderColor.green, sliderColor.blue, sliderColor.alpha)
        surface.drawTexture(Texture.BLANK, xSlider, ySlider, sliderWidth, sliderHeight)
    }

    override fun updateChildLayout()
    {
        if(hidden)
        {
            val sliderTravelDist = (height.value - sliderHeight - 2f * sliderPadding)
            if(sliderTravelDist > 0)
                hidden = false
        }
        super.updateChildLayout()
    }

    fun setOnScroll(callback: (Float) -> Unit)
    {
        this.onScrollCallback = callback
    }

    fun bind(scrollable: Scrollable)
    {
        boundScrollable = scrollable
    }
}