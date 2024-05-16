package no.njoh.pulseengine.core.input
import no.njoh.pulseengine.core.asset.types.Cursor
import no.njoh.pulseengine.core.console.Subscription

interface Input
{
    val xMouse: Float
    val yMouse: Float
    val xWorldMouse: Float
    val yWorldMouse: Float
    val xdMouse: Float
    val ydMouse: Float
    val xScroll: Float
    val yScroll: Float
    val textInput: String
    val clickedKeys: List<Key>
    val gamepads: List<Gamepad>

    fun isPressed(key: Key): Boolean

    /**
     * Returns True if the given [MouseButton] is currently pressed.
     */
    fun isPressed(btn: MouseButton): Boolean

    /**
     * Returns True if the given keyboard [Key] was pressed this frame.
     */
    fun wasClicked(key: Key): Boolean

    /**
     * Returns True if the given [MouseButton] was pressed this frame.
     */
    fun wasClicked(btn: MouseButton): Boolean

    /**
     * Returns True if the given keyboard [Key] was released this frame.
     */
    fun wasReleased(key: Key): Boolean

    /**
     * Returns True if the given [MouseButton] was released this frame.
     */
    fun wasReleased(btn: MouseButton): Boolean

    /**
     * Sets a callback to be called when a key is pressed.
     * @return A subscription that can be used to remove the callback.
     */
    fun setOnKeyPressed(callback: (Key) -> Unit): Subscription

    /**
     * Sets the clipboard content to the given text.
     */
    fun setClipboard(text: String)
    fun getClipboard(): String
    fun setOnKeyPressed(callback: (Key) -> Unit): Subscription
    fun requestFocus(focusArea: FocusArea)
    fun acquireFocus(focusArea: FocusArea)
    fun releaseFocus(focusArea: FocusArea)
    fun hasFocus(focusArea: FocusArea): Boolean
    fun hasHoverFocus(focusArea: FocusArea): Boolean
    fun setCursorType(cursorType: CursorType)
    fun setCursorPosition(x: Float, y: Float)
    fun setCursorMode(cursorMode: CursorMode)
}

interface InputInternal : Input
{
    override var xWorldMouse: Float
    override var yWorldMouse: Float

    fun init(windowHandle: Long)
    fun destroy()
    fun pollEvents()
    fun setOnFocusChanged(callback: (Boolean) -> Unit)
    fun createCursor(cursor: Cursor)
    fun deleteCursor(cursor: Cursor)
    fun getCursorsToLoad(): List<Cursor>
}