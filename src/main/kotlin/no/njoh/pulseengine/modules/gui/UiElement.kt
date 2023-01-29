package no.njoh.pulseengine.modules.gui

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.input.Mouse
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.modules.gui.elements.Label
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

abstract class UiElement(
    val x: Position,
    val y: Position,
    val width: Size,
    val height: Size
) {
    /** An ID for the UI element. */
    var id: String? = null

    /** A reference to the parent element containing this one. */
    var parent: UiElement? = null
        private set

    /** The list of children this UiElement contains. */
    val children = mutableListOf<UiElement>()

    /** Reference to a UiElement that will be updated and drawn after the main draw/update pass. */
    var popup: UiElement? = null

    /** The padding added to the outside of each side.*/
    val padding = Padding()

    /** The area to capture input focus. */
    val area = FocusArea(0f, 0f, 0f, 0f)

    /** The minimum width this element can have. */
    open var minWidth = ScaledValue.of(0f)

    /** The minimum height this element can have. */
    open var minHeight = ScaledValue.of(0f)

    /** The maximum width this element can have. */
    open var maxWidth = ScaledValue.of(10000f)

    /** The minimum heihgt this element can have. */
    open var maxHeight = ScaledValue.of(10000f)

    /** True if this element should capture input focus. */
    open var focusable = true

    /** True if the content of this element should not be rendered outside the bounds of this element. */
    open var renderOnlyInside = false

    /** True if this element should not be visible in the UI graph. */
    var hidden = false
        set (isHidden)
        {
            if (field != isHidden)
                setLayoutDirty()
            field = isHidden
        }

    /** True if the mouse is currently inside the bounds of the element. */
    var mouseInsideArea = false
        private set

    private var created = false
    private var preventRender = false
    private var dirtyLayout = false
    private var onKeyPressed: ((Key) -> Boolean)? = null

    init
    {
        x.setOnUpdated(::setLayoutDirty)
        y.setOnUpdated(::setLayoutDirty)
        width.setOnUpdated(::setLayoutDirty)
        height.setOnUpdated(::setLayoutDirty)
        padding.setOnUpdated(::setLayoutDirty)
    }

    // Updating the UI element
    // ---------------------------------------------------------------------------------------------------------

    fun update(engine: PulseEngine)
    {
        // Call onCreate only once
        if (!created)
        {
            created = true
            onCreate(engine)
        }

        // Update independent of visibility
        onVisibilityIndependentUpdate(engine)

        // Do not update element if it is not visible
        if (!isVisible())
            return

        // Update mouse related state
        updateMouseInputState(engine)

        // Update this element
        onUpdate(engine)

        // Update all children elements and take note if their layout is dirty
        children.forEachFast()
        {
            it.update(engine)
            if (it.dirtyLayout || it.popup?.dirtyLayout == true)
                setLayoutDirty()
        }

        // Check if this is the root node
        if (parent == null)
        {
            // Update the popups after the all children of the root node has been updated
            updatePopup(engine)

            // Update the layout if it's dirty
            if (dirtyLayout || engine.window.wasResized)
            {
                alignWithin(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
                updateLayout()
                setLayoutClean()
            }
        }
    }

    private fun updateMouseInputState(engine: PulseEngine)
    {
        val isNotOutsideParent = parent?.mouseInsideArea != false || parent?.popup === this
        val insideArea = area.isInside(engine.input.xMouse, engine.input.yMouse) && isNotOutsideParent
        val mouseInsideStateChanged = (mouseInsideArea != insideArea)
        mouseInsideArea = insideArea

        // Handle mouse scroll
        val xScroll = engine.input.xScroll
        val yScroll = engine.input.yScroll
        if ((xScroll != 0f || yScroll != 0f) && engine.input.hasHoverFocus(area))
            handleScrollEvent(xScroll, yScroll)

        // Do not request focus or update mouse callbacks if the element is not focusable
        if (!focusable)
            return

        // Request input focus for this area
        engine.input.requestFocus(area)

        // Handle key presses
        val keys = engine.input.clickedKeys
        if (keys.isNotEmpty())
            keys.forEachFast { handleKeyPress(it) }

        // Update callbacks on state change
        if (mouseInsideStateChanged)
        {
            if (insideArea) onMouseEnter(engine) else onMouseLeave(engine)
        }

        if (insideArea && engine.input.wasClicked(Mouse.LEFT))
            onMouseClicked(engine)
    }

    open fun handleKeyPress(key: Key) {
        val handled = onKeyPressed?.invoke(key) ?: false
        if (!handled)
            parent?.handleKeyPress(key)
    }

    private fun handleScrollEvent(xScroll: Float, yScroll: Float)
    {
        if (this is Scrollable)
        {
            onScroll(xScroll, yScroll)
            setLayoutDirty()
        }
        else parent?.handleScrollEvent(xScroll, yScroll)
    }

    private fun updatePopup(engine: PulseEngine)
    {
        if (!isVisible())
            return

        popup?.update(engine)
        children.forEachFast { it.updatePopup(engine) }
    }

    fun updateLayout()
    {
        width.setQuiet(width.value.coerceIn(minWidth.value, maxWidth.value))
        height.setQuiet(height.value.coerceIn(minHeight.value, maxHeight.value))
        updateFocusArea()
        updateChildLayout()
        updatePopupLayout()
    }

    private fun updateFocusArea()
    {
        var x0 = x.value
        var y0 = y.value
        var x1 = x.value + width.value
        var y1 = y.value + height.value

        val p = parent
        if (p != null && this !== p.popup) // Popups are not constrained to the parent area
        {
            val xMin = p.area.x0
            val yMin = p.area.y0
            val xMax = p.area.x1
            val yMax = p.area.y1
            x0 = x0.coerceIn(xMin, xMax)
            y0 = y0.coerceIn(yMin, yMax)
            x1 = x1.coerceIn(xMin, xMax)
            y1 = y1.coerceIn(yMin, yMax)
        }

        area.update(x0, y0, x1, y1)
    }

    open fun updateChildLayout()
    {
        children.forEachFast()
        {
            it.alignWithin(x.value, y.value, width.value, height.value)
            it.updateLayout()
            it.setLayoutClean()
        }
    }

    open fun updatePopupLayout()
    {
        val popup = popup ?: return
        popup.alignWithin(x.value, y.value, width.value, height.value)
        popup.updateLayout()
        popup.setLayoutClean()
    }

    private fun alignWithin(xPos: Float, yPos: Float, availableWidth: Float, availableHeight: Float)
    {
        val newWidth = width.calculate(availableWidth - (padding.left + padding.right)).coerceIn(minWidth.value, maxWidth.value)
        val newHeight = height.calculate(availableHeight - (padding.top + padding.bottom)).coerceIn(minHeight.value, maxHeight.value)
        val xNew = x.calculate(minVal = xPos + padding.left, maxVal = xPos + availableWidth - newWidth)
        val yNew = y.calculate(minVal = yPos + padding.top, maxVal = yPos + availableHeight - newHeight)

        width.setQuiet(newWidth)
        height.setQuiet(newHeight)
        x.setQuiet(xNew)
        y.setQuiet(yNew)
    }

    // Rendering the UI element
    // ---------------------------------------------------------------------------------------------------------

    open fun render(engine: PulseEngine, surface: Surface2D)
    {
        if (!isVisible())
            return

        if (renderOnlyInside)
        {
            surface.drawWithin(x.value, y.value, width.value, height.value)
            {
                onRender(engine, surface)
                children.forEachFast { it.render(engine, surface) }
            }
        }
        else
        {
            onRender(engine, surface)
            children.forEachFast { it.render(engine, surface) }
        }

        // Start rendering the popups from the root node
        if (parent == null)
            renderPopup(engine, surface)
    }

    private fun renderPopup(engine: PulseEngine, surface: Surface2D)
    {
        if (!isVisible())
            return

        popup?.render(engine, surface)
        children.forEachFast { it.renderPopup(engine, surface) }
    }

    fun isVisible() = !hidden && !preventRender

    fun preventRender(state: Boolean)
    {
        preventRender = state
    }

    fun printStructure(indent: Int = 0)
    {
        var data = if (this is Label) " (${this.text})" else ""
        data += popup?.let { " popup visible: (${it.isVisible()})" } ?: ""
        data += " id: $id"
        println(" ".repeat(indent) + this::class.simpleName + data)
        for (child in children)
            child.printStructure(indent + 2)
    }

    // Children operations
    // ---------------------------------------------------------------------------------------------------------

    fun addChildren(vararg uiElements: UiElement)
    {
        children.addAll(uiElements)
        uiElements.forEachFast { it.parent = this }
        setLayoutDirty()
    }

    fun addChildren(uiElements: List<UiElement>)
    {
        children.addAll(uiElements)
        uiElements.forEachFast { it.parent = this }
        setLayoutDirty()
    }

    fun insertChild(element: UiElement, index: Int)
    {
        children.add(index, element)
        element.parent = this
        setLayoutDirty()
    }

    fun replaceChild(oldElement: UiElement, newElement: UiElement)
    {
        val index = children.indexOf(oldElement)
        if (index != -1)
            children[index] = newElement
        else
            children.add(newElement)
        newElement.parent = this
        setLayoutDirty()
    }

    fun removeChildren(vararg uiElements: UiElement)
    {
        children.removeAll(uiElements)
        setLayoutDirty()
    }

    fun removeChildren(uiElements: List<UiElement>)
    {
        children.removeAll(uiElements)
        setLayoutDirty()
    }

    fun clearChildren()
    {
        if (children.isNotEmpty())
        {
            children.clear()
            setLayoutDirty()
        }
    }

    fun addPopup(popup: UiElement)
    {
        popup.parent = this
        this.popup = popup
    }

    // Setters
    // ---------------------------------------------------------------------------------------------------------

    fun setLayoutDirty()
    {
        dirtyLayout = true
    }

    fun setLayoutClean()
    {
        dirtyLayout = false
    }

    fun setOnKeyPressed(callback: (Key) -> Boolean)
    {
        this.onKeyPressed = callback
    }

    // Abstract and open functions implemented by concrete UI sub-classes
    // ---------------------------------------------------------------------------------------------------------

    open fun onCreate(engine: PulseEngine) { }
    open fun onMouseEnter(engine: PulseEngine) { }
    open fun onMouseLeave(engine: PulseEngine) { }
    open fun onMouseClicked(engine: PulseEngine) { }
    open fun onVisibilityIndependentUpdate(engine: PulseEngine) { }

    protected abstract fun onUpdate(engine: PulseEngine)
    protected abstract fun onRender(engine: PulseEngine, surface: Surface2D)
}