package no.njoh.pulseengine.core.input
import no.njoh.pulseengine.core.asset.types.Cursor
import no.njoh.pulseengine.core.console.Subscription

interface Input
{
    /** The current mouse X-position in screen space (left = 0.0, right = window width) */
    val xMouse: Float

    /** The current mouse Y-position in screen space (top = 0.0, bottom = window height) */
    val yMouse: Float

    /** The current mouse X-position in world space */
    val xWorldMouse: Float

    /** The current mouse Y-position in world space */
    val yWorldMouse: Float

    /** The change in mouse X-position since last frame in screen space */
    val xdMouse: Float

    /** The change in mouse Y-position since last frame in screen space */
    val ydMouse: Float

    /** The change in horizontal scroll since last frame */
    val xScroll: Float

    /** The change in vertical scroll since last frame */
    val yScroll: Float

    /** The text written since last frame */
    val textInput: String

    /** The keys clicked since last frame */
    val clickedKeys: List<Key>

    /** All connected gamepads */
    val gamepads: List<Gamepad>

    /**
     * Returns true if the given keyboard [Key] is currently pressed.
     */
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

    /**
     * Returns the current clipboard content.
     */
    fun getClipboard(): String

    /**
     * Requests focus for the given [FocusArea].
     * If this focus area is not currently focused, the engines [Input] module will be
     * swapped with the [UnfocusedInput] implementation where no key or mouse presses are registered.
     */
    fun requestFocus(focusArea: FocusArea)

    /**
     * Acquires focus for the given [FocusArea].
     * Makes this area the currently focused one and updates the input module to use the active implementation
     * where all key and mouse presses are registered.
     */
    fun acquireFocus(focusArea: FocusArea)

    /**
     * Releases focus for the given [FocusArea] if it is focused and gives the previously focused area back its focus.
     */
    fun releaseFocus(focusArea: FocusArea)

    /**
     * Returns true if the given [FocusArea] is currently focused.
     */
    fun hasFocus(focusArea: FocusArea): Boolean

    /**
     * Returns true if the mouse is currently hovering over the given [FocusArea].
     */
    fun hasHoverFocus(focusArea: FocusArea): Boolean

    /**
     * Sets the active [CursorType]. Default is [CursorType.ARROW].
     */
    fun setCursorType(cursorType: CursorType)

    /**
     * Sets the position of the cursor in screen space.
     */
    fun setCursorPosition(x: Float, y: Float)

    /**
     * Sets the active [CursorMode]. Default is [CursorMode.NORMAL].
     */
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