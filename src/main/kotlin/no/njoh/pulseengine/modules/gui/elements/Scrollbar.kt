package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.input.CursorType
import no.njoh.pulseengine.core.input.Mouse
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Scrollable
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.UiElement
import no.njoh.pulseengine.modules.gui.UiUtil.firstElementOrNull
import kotlin.math.max

class Scrollbar(
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(Position.auto(), Position.auto(), width, height) {

    var sliderColor = Color(1f, 1f, 1f)
    var sliderColorHover = Color(1f, 1f, 1f)
    var bgColor = Color(1f, 1f, 1f, 0f)
    var cornerRadius = 0f
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

    override fun onVisibilityIndependentUpdate(engine: PulseEngine)
    {
        val scrollable = boundScrollable ?: return
        val shouldHide = scrollable.getUsedSpaceFraction() < 1f
        if (scrollable.hideScrollbarOnEnoughSpaceAvailable && shouldHide == isVisible())
        {
            preventRender(shouldHide)
            setLayoutDirty()
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        val xMouse = engine.input.xMouse
        val yMouse = engine.input.yMouse
        var scroll = 0

        isVerticalSlider = height.value >= width.value
        isMouseOverSlider = (xMouse >= xSlider && xMouse < xSlider + sliderWidth && yMouse >= ySlider && yMouse < ySlider + sliderHeight)
        boundScrollable?.let()
        {
            sliderLengthFraction = (1f / max(0.0000001f, it.getUsedSpaceFraction())).coerceIn(0.2f, 1f)

            // Get scroll amount when the bound scrollable has hover focus
            val mouseScroll = engine.input.scroll
            if (mouseScroll != 0 && it.hasHoverFocus(engine))
                scroll = mouseScroll
        }

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

            updateSliderFraction(engine.input.ydMouse, scroll, sliderTravelDist)
        }
        else
        {
            sliderHeight = height.value - 2f * sliderPadding
            sliderWidth = width.value * sliderLengthFraction - 2f * sliderPadding

            val sliderTravelDist = (width.value - sliderWidth - 2f * sliderPadding)

            ySlider = y.value + sliderPadding
            xSlider = x.value + sliderPadding + sliderFraction * sliderTravelDist

            updateSliderFraction(engine.input.ydMouse, scroll, sliderTravelDist)
        }

        if (isMouseOverSlider)
            engine.input.setCursor(CursorType.ARROW)
    }

    private fun updateSliderFraction(mouseChange: Float, scroll: Int, sliderTravelDist: Float)
    {
        if (sliderTravelDist > 0f)
        {
            val scrollChange = -scroll * (sliderTravelDist * sliderLengthFraction * 0.2f)
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
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius)

        val sliderColor = if (isMouseOverSlider || sliderGrabbed) sliderColorHover else sliderColor
        surface.setDrawColor(sliderColor.red, sliderColor.green, sliderColor.blue, sliderColor.alpha)
        surface.drawTexture(Texture.BLANK, xSlider, ySlider, sliderWidth, sliderHeight, cornerRadius = cornerRadius)
    }

    override fun updateChildLayout()
    {
        if (!isVisible())
        {
            val sliderTravelDist = (height.value - sliderHeight - 2f * sliderPadding)
            if (sliderTravelDist > 0)
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

    private fun Scrollable.hasHoverFocus(engine: PulseEngine): Boolean =
        this is UiElement && this.firstElementOrNull { engine.input.hasHoverFocus(it.area) } != null
}