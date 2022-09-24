package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.input.CursorType
import no.njoh.pulseengine.core.input.Mouse
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.gui.*
import no.njoh.pulseengine.modules.gui.ScrollDirection.*
import no.njoh.pulseengine.modules.gui.ScrollbarVisibility.ALWAYS_VISIBLE
import no.njoh.pulseengine.modules.gui.ScrollbarVisibility.HIDDEN
import no.njoh.pulseengine.modules.gui.UiUtil.firstElementOrNull
import kotlin.math.max

class Scrollbar(
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(Position.auto(), Position.auto(), width, height) {

    var scrollDirection = VERTICAL
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
    private var isMouseOverSlider = false
    private var sliderGrabbed = false
    private var sliderHeight = 0f
    private var sliderWidth = 0f
    private var xSlider = 0f
    private var ySlider = 0f

    override fun onVisibilityIndependentUpdate(engine: PulseEngine)
    {
        if (boundScrollable == null)
            return

        val visibility = boundScrollable.getVisibility()
        val shouldHide = boundScrollable.getUsedSpaceFraction() < 1f || visibility == HIDDEN
        if (visibility != ALWAYS_VISIBLE && shouldHide == isVisible())
        {
            preventRender(shouldHide)
            setLayoutDirty()
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (boundScrollable == null)
            return

        val xMouse = engine.input.xMouse
        val yMouse = engine.input.yMouse
        var xScroll = 0
        var yScroll = 0

        // Get scroll amount when the bound scrollable has hover focus
        if (boundScrollable?.hasHoverFocus(engine) == true)
        {
            xScroll = engine.input.xScroll
            yScroll = engine.input.yScroll
        }

        // Check if mouse is grabbing slider
        isMouseOverSlider = (xMouse >= xSlider && xMouse < xSlider + sliderWidth && yMouse >= ySlider && yMouse < ySlider + sliderHeight)
        if (engine.input.isPressed(Mouse.LEFT))
        {
            if (isMouseOverSlider)
                sliderGrabbed = true
        }
        else sliderGrabbed = false

        // Update length of slider based
        sliderLengthFraction = (1f / max(0.0000001f, boundScrollable.getUsedSpaceFraction())).coerceIn(0.2f, 1f)

        if (scrollDirection == VERTICAL)
        {
            sliderHeight = height.value * sliderLengthFraction - 2f * sliderPadding
            sliderWidth = width.value - 2f * sliderPadding

            val sliderTravelDist = (height.value - sliderHeight - 2f * sliderPadding)

            xSlider = x.value + sliderPadding
            ySlider = y.value + sliderPadding + sliderFraction * sliderTravelDist

            updateSliderFraction(engine.input.ydMouse, yScroll, sliderTravelDist)
        }
        else if (scrollDirection == HORIZONTAL)
        {
            sliderHeight = height.value - 2f * sliderPadding
            sliderWidth = width.value * sliderLengthFraction - 2f * sliderPadding

            val sliderTravelDist = (width.value - sliderWidth - 2f * sliderPadding)

            ySlider = y.value + sliderPadding
            xSlider = x.value + sliderPadding + sliderFraction * sliderTravelDist

            updateSliderFraction(engine.input.xdMouse, xScroll, sliderTravelDist)
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
                boundScrollable.setScrollFraction(sliderFraction)
            }
        }
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
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

    fun bind(scrollable: Scrollable, direction: ScrollDirection)
    {
        scrollDirection = direction
        boundScrollable = scrollable
    }

    private fun Scrollable?.getUsedSpaceFraction() = when (scrollDirection)
    {
        VERTICAL -> (this as? VerticallyScrollable)?.getVerticallyUsedSpaceFraction() ?: 0f
        HORIZONTAL -> (this as? HorizontallyScrollable)?.getHorizontallyUsedSpaceFraction() ?: 0f
    }

    private fun Scrollable?.setScrollFraction(fraction: Float) = when (scrollDirection)
    {
        VERTICAL -> (this as? VerticallyScrollable)?.setVerticalScroll(fraction)
        HORIZONTAL -> (this as? HorizontallyScrollable)?.setHorizontalScroll(fraction)
    }

    private fun Scrollable?.getVisibility() = when (scrollDirection)
    {
        VERTICAL -> (this as? VerticallyScrollable)?.verticalScrollbarVisibility ?: HIDDEN
        HORIZONTAL -> (this as? HorizontallyScrollable)?.horizontalScrollbarVisibility ?: HIDDEN
    }

    private fun Scrollable.hasHoverFocus(engine: PulseEngine): Boolean =
        this is UiElement && this.firstElementOrNull { engine.input.hasHoverFocus(it.area) } != null
}