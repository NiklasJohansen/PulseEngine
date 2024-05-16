package no.njoh.pulseengine.core.input

import no.njoh.pulseengine.core.asset.types.Cursor

/**
 * An input implementation that does not expose actual key and mouse presses.
 */
class UnfocusedInput(private val activeInput: InputInternal) : InputInternal
{
    // All key and mouse presses are returned as if they did not happen

    override val textInput   = ""
    override val clickedKeys = emptyList<Key>()

    override fun isPressed(key: Key)           = false
    override fun isPressed(btn: MouseButton)   = false
    override fun wasClicked(key: Key)          = false
    override fun wasClicked(btn: MouseButton)  = false
    override fun wasReleased(key: Key)         = false
    override fun wasReleased(btn: MouseButton) = false

    // Passed through to the active input and returns the real values

    override var xWorldMouse = 0f; get() = activeInput.xWorldMouse
    override var yWorldMouse = 0f; get() = activeInput.yWorldMouse
    override val xMouse  get() = activeInput.xMouse
    override val yMouse  get() = activeInput.yMouse
    override val xdMouse get() = activeInput.xdMouse
    override val ydMouse get() = activeInput.ydMouse
    override val xScroll get() = activeInput.xScroll
    override val yScroll get() = activeInput.yScroll
    override val gamepads      = activeInput.gamepads

    override fun init(windowHandle: Long)                       = activeInput.init(windowHandle)
    override fun destroy()                                      = activeInput.destroy()
    override fun pollEvents()                                   = activeInput.pollEvents()
    override fun setOnFocusChanged(callback: (Boolean) -> Unit) = activeInput.setOnFocusChanged(callback)
    override fun setOnKeyPressed(callback: (Key) -> Unit)       = activeInput.setOnKeyPressed(callback)

    override fun requestFocus(focusArea: FocusArea)             = activeInput.requestFocus(focusArea)
    override fun acquireFocus(focusArea: FocusArea)             = activeInput.acquireFocus(focusArea)
    override fun releaseFocus(focusArea: FocusArea)             = activeInput.releaseFocus(focusArea)
    override fun hasFocus(focusArea: FocusArea)                 = activeInput.hasFocus(focusArea)
    override fun hasHoverFocus(focusArea: FocusArea)            = activeInput.hasHoverFocus(focusArea)

    override fun getCursorsToLoad()                             = activeInput.getCursorsToLoad()
    override fun deleteCursor(cursor: Cursor)                   = activeInput.deleteCursor(cursor)
    override fun createCursor(cursor: Cursor)                   = activeInput.createCursor(cursor)
    override fun setCursorType(cursorType: CursorType)          = activeInput.setCursorType(cursorType)
    override fun setCursorPosition(x: Float, y: Float)          = activeInput.setCursorPosition(x, y)
    override fun setCursorMode(cursorMode: CursorMode)          = activeInput.setCursorMode(cursorMode)

    override fun getClipboard(): String                         = activeInput.getClipboard()
    override fun setClipboard(text: String)                     = activeInput.setClipboard(text)
}