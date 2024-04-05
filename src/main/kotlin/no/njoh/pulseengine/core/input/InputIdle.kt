package no.njoh.pulseengine.core.input

import no.njoh.pulseengine.core.asset.types.Cursor

class InputIdle(private val activeInput: InputInternal) : InputInternal
{
    override val xMouse get() = activeInput.xMouse
    override val yMouse get() = activeInput.yMouse
    override var xWorldMouse = 0f
        get() = activeInput.xWorldMouse
    override var yWorldMouse = 0f
        get() = activeInput.yWorldMouse
    override val xdMouse get() = activeInput.xdMouse
    override val ydMouse get() = activeInput.ydMouse
    override val xScroll get() = activeInput.xScroll
    override val yScroll get() = activeInput.yScroll
    override val textInput: String = ""
    override val clickedKeys = emptyList<Key>()
    override val gamepads = activeInput.gamepads
    override fun init(windowHandle: Long) {}
    override fun cleanUp() {}
    override fun pollEvents() {}
    override fun isPressed(key: Key) = false
    override fun isPressed(btn: Mouse) = false
    override fun wasClicked(key: Key) = false
    override fun wasClicked(btn: Mouse) = false
    override fun wasReleased(key: Key) = false
    override fun wasReleased(btn: Mouse) = false
    override fun setClipboard(text: String)  {}
    override fun getClipboard(): String = ""
    override fun setOnFocusChanged(callback: (Boolean) -> Unit) = activeInput.setOnFocusChanged(callback)
    override fun createCursor(cursor: Cursor) = activeInput.createCursor(cursor)
    override fun deleteCursor(cursor: Cursor) = activeInput.deleteCursor(cursor)
    override fun getCursorsToLoad() = activeInput.getCursorsToLoad()
    override fun setOnKeyPressed(callback: (Key) -> Unit) = activeInput.setOnKeyPressed(callback)
    override fun requestFocus(focusArea: FocusArea) = activeInput.requestFocus(focusArea)
    override fun acquireFocus(focusArea: FocusArea) = activeInput.acquireFocus(focusArea)
    override fun releaseFocus(focusArea: FocusArea) = activeInput.releaseFocus(focusArea)
    override fun hasFocus(focusArea: FocusArea): Boolean = activeInput.hasFocus(focusArea)
    override fun hasHoverFocus(focusArea: FocusArea): Boolean = activeInput.hasHoverFocus(focusArea)
    override fun setCursorType(cursorType: CursorType) = activeInput.setCursorType(cursorType)
    override fun setCursorPosition(x: Float, y: Float) = activeInput.setCursorPosition(x, y)
    override fun setCursorMode(cursorMode: CursorMode) = activeInput.setCursorMode(cursorMode)
}