package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.input.CursorType
import no.njoh.pulseengine.core.input.MouseButton
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.modules.gui.*
import no.njoh.pulseengine.modules.gui.ScrollDirection.*
import no.njoh.pulseengine.modules.gui.ScrollbarVisibility.ALWAYS_VISIBLE
import no.njoh.pulseengine.modules.gui.ScrollbarVisibility.HIDDEN
import kotlin.math.max
import kotlin.math.min

class Scrollbar(
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(Position.auto(), Position.auto(), width, height) {

    var sliderColor = Color.WHITE
    var sliderColorHover = Color.WHITE
    var bgColor = Color.BLANK
    var sliderLengthFraction = 0.1f
    var sliderPadding = ScaledValue.of(0f)
    var minSliderLength = ScaledValue.of(16f)
    var scrollDistance = ScaledValue.of(50f)
    var cornerRadius = ScaledValue.of(0f)
    var sliderFraction = 0f
        private set

    var scrollDirection = VERTICAL
    var scrollable: Scrollable? = null

    private var onScrollCallback: (Float) -> Unit = { }
    private var isMouseOverSlider = false
    private var sliderGrabbed = false
    private var sliderHeight = 0f
    private var sliderWidth = 0f
    private var xSlider = 0f
    private var ySlider = 0f

    override fun onVisibilityIndependentUpdate(engine: PulseEngine)
    {
        if (scrollable == null)
            return

        val visibility = scrollable.getVisibility()
        val shouldHide = scrollable.getUsedSpaceFraction() < 1f || visibility == HIDDEN
        if (visibility != ALWAYS_VISIBLE && shouldHide == isVisible())
        {
            preventRender(shouldHide)
            setLayoutDirty()
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        val scrollable = scrollable ?: return

        // Get scroll amount when the bound scrollable has hover focus
        var xScroll = 0f
        var yScroll = 0f
        if (scrollable is VerticallyScrollable)
        {
            yScroll = scrollable.yScroll
            scrollable.yScroll = 0f
        }
        if (scrollable is HorizontallyScrollable)
        {
            xScroll = scrollable.xScroll
            scrollable.xScroll = 0f
        }

        // Check if mouse is hovering over slider
        val xMouse = engine.input.xMouse
        val yMouse = engine.input.yMouse
        isMouseOverSlider = (xMouse >= xSlider && xMouse < xSlider + sliderWidth && yMouse >= ySlider && yMouse < ySlider + sliderHeight)

        // Check if mouse is grabbing slider
        if (engine.input.isPressed(MouseButton.LEFT))
        {
            if (isMouseOverSlider)
                sliderGrabbed = true
        }
        else sliderGrabbed = false

        // Update length of slider
        val length = if (scrollDirection == VERTICAL) height.value else width.value
        val minFraction = min(1f, (minSliderLength + sliderPadding) / max(1f, length))
        val usedSpaceFraction = 1f / max(0.0000001f, scrollable.getUsedSpaceFraction())
        sliderLengthFraction = usedSpaceFraction.coerceIn(minFraction, 1f)

        // Update slider position
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
            engine.input.setCursorType(CursorType.ARROW)
    }

    private fun updateSliderFraction(mouseChange: Float, scroll: Float, sliderTravelDist: Float)
    {
        if (sliderTravelDist > 0f)
        {
            val mouseMoveFraction = if (sliderGrabbed) mouseChange / sliderTravelDist else 0f
            val maxScrollDist = height.value * max(0f,scrollable.getUsedSpaceFraction() - 1f)
            val scrollChangeFraction = -scroll * (scrollDistance / maxScrollDist)
            val fractionChange = mouseMoveFraction + scrollChangeFraction
            if (fractionChange != 0f && (sliderGrabbed || scroll != 0f))
            {
                sliderFraction = (sliderFraction + fractionChange).coerceIn(0f, 1f)
                onScrollCallback(sliderFraction)
                scrollable.setScrollFraction(sliderFraction)
            }
        }
    }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        surface.setDrawColor(bgColor.red, bgColor.green, bgColor.blue, bgColor.alpha)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius.value)

        val sliderColor = if (isMouseOverSlider || sliderGrabbed) sliderColorHover else sliderColor
        surface.setDrawColor(sliderColor.red, sliderColor.green, sliderColor.blue, sliderColor.alpha)
        surface.drawTexture(Texture.BLANK, xSlider, ySlider, sliderWidth, sliderHeight, cornerRadius = cornerRadius.value)
    }

    override fun updateChildLayout()
    {
        if (!isVisible())
        {
            val sliderTravelDist = when (scrollDirection)
            {
                VERTICAL -> (height.value - sliderHeight - 2f * sliderPadding)
                HORIZONTAL -> (width.value - sliderWidth - 2f * sliderPadding)
            }

            if (sliderTravelDist > 0)
                hidden = false
        }
        super.updateChildLayout()
    }

    fun bind(scrollable: Scrollable, direction: ScrollDirection)
    {
        this.scrollDirection = direction
        this.scrollable = scrollable
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
}