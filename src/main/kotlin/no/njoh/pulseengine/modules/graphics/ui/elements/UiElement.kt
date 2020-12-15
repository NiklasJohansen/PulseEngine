package no.njoh.pulseengine.modules.graphics.ui.elements

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.FocusArea
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Padding
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size

abstract class UiElement(
    val x: Position,
    val y: Position,
    val width: Size,
    val height: Size
) {
    var id: String? = null
    var parent: UiElement? = null
        private set
    val children = mutableListOf<UiElement>()
    val padding: Padding = Padding()
    var popup: UiElement? = null
    val area = FocusArea(0f, 0f, 0f, 0f)

    var minWidth = 0f
    var minHeight = 0f
    var maxWidth = 10000f
    var maxHeight = 10000f
    var intractable = true
    var hidden = false
        set (isHidden)
        {
            if (field != isHidden)
                setLayoutDirty()
            field = isHidden
        }

    private var created = false
    private var dirtyLayout = false
    var mouseInsideArea = false
        private set

    init
    {
        x.setOnUpdated(::setLayoutDirty)
        y.setOnUpdated(::setLayoutDirty)
        width.setOnUpdated(::setLayoutDirty)
        height.setOnUpdated(::setLayoutDirty)
        padding.setOnUpdated(::setLayoutDirty)
    }

    fun addChildren(vararg uiElements: UiElement)
    {
        children.addAll(uiElements)
        children.forEach { it.parent = this }
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

    fun update(engine: PulseEngine)
    {
        if (!created)
        {
            created = true
            onCreate(engine)
        }

        if (hidden)
            return

        if (intractable)
        {
            engine.input.requestFocus(area)

            val insideArea = area.isInside(engine.input.xMouse, engine.input.yMouse)
            if (insideArea && engine.input.wasClicked(Mouse.LEFT))
                onMouseClicked(engine)

            if (mouseInsideArea != insideArea)
            {
                if (insideArea) onMouseEnter(engine) else onMouseLeave(engine)
                mouseInsideArea = insideArea
            }

            onUpdate(engine)
        }

        children.forEach { child ->
            child.update(engine)
            if (child.dirtyLayout || child.popup?.dirtyLayout == true)
                setLayoutDirty()
        }

        if (parent == null)
        {
            updatePopup(engine)
            if (dirtyLayout || engine.window.wasResized)
            {
                alignWithin(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
                updateLayout()
                setLayoutClean()
            }
        }
    }

    open fun render(surface: Surface2D)
    {
        if (!hidden)
        {
            onRender(surface)
            children.forEach { it.render(surface) }

            if (parent == null)
                renderPopup(surface)
        }
    }

    fun setLayoutDirty()
    {
        dirtyLayout = true
    }

    fun setLayoutClean()
    {
        dirtyLayout = false
    }

    fun updateLayout()
    {
        width.setQuiet(width.value.coerceIn(minWidth, maxWidth))
        height.setQuiet(height.value.coerceIn(minHeight, maxHeight))
        area.update(x.value, y.value, x.value + width.value, y.value + height.value)

        updateChildLayout()
        updatePopupLayout()
    }

    open fun updateChildLayout()
    {
        for (child in children)
        {
            child.alignWithin(x.value, y.value, width.value, height.value)
            child.updateLayout()
            child.setLayoutClean()
        }
    }

    open fun updatePopupLayout()
    {
        popup?.let { popup ->
            popup.alignWithin(x.value, y.value, width.value, height.value)
            popup.updateLayout()
            popup.setLayoutClean()
        }
    }

    private fun alignWithin(xPos: Float, yPos: Float, availableWidth: Float, availableHeight: Float)
    {
        val newWidth = width.calculate(availableWidth - (padding.left + padding.right)).coerceIn(minWidth, maxWidth)
        val newHeight = height.calculate(availableHeight - (padding.top + padding.bottom)).coerceIn(minHeight, maxHeight)
        val xNew = x.calculate(minVal = xPos + padding.left, maxVal = availableWidth - newWidth)
        val yNew = y.calculate(minVal = yPos + padding.top, maxVal = availableHeight - newHeight)

        width.setQuiet(newWidth)
        height.setQuiet(newHeight)
        x.setQuiet(xNew)
        y.setQuiet(yNew)
    }

    private fun updatePopup(engine: PulseEngine)
    {
        if (hidden)
            return

        popup?.update(engine)
        children.forEach { it.updatePopup(engine) }
    }

    private fun renderPopup(surface: Surface2D)
    {
        if (hidden)
            return

        popup?.render(surface)
        children.forEach { it.renderPopup(surface) }
    }

    fun printStructure(indent: Int = 0)
    {
        var data = if (this is Label) " (${this.text})" else ""
        data += popup?.let { " popup hidden: (${it.hidden})" } ?: ""
        data += " id: $id"
        println(" ".repeat(indent) + this::class.simpleName + data)
        for (child in children)
            child.printStructure(indent + 2)
    }

    open fun onCreate(engine: PulseEngine) { }
    open fun onHiddenUpdate(engine: PulseEngine) { }
    open fun onMouseEnter(engine: PulseEngine) { }
    open fun onMouseLeave(engine: PulseEngine) { }
    open fun onMouseClicked(engine: PulseEngine) { }

    protected abstract fun onUpdate(engine: PulseEngine)
    protected abstract fun onRender(surface: Surface2D)
}