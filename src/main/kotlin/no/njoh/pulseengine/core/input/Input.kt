package no.njoh.pulseengine.core.input
import no.njoh.pulseengine.core.asset.types.Cursor
import no.njoh.pulseengine.core.console.Subscription

interface Input
{
    /** The current screen space mouse X-position (left = 0.0, right = window width) */
    val xMouse: Float

    /** The current screen space mouse Y-position (top = 0.0, bottom = window height) */
    val yMouse: Float

    /** The current world space mouse X-position in relation to where the main camera is looking */
    val xWorldMouse: Float

    /** The current world space mouse Y-position in relation to where the main camera is looking */
    val yWorldMouse: Float

    /** The change in mouse X-position since last frame in screen space */
    val xdMouse: Float

    /** The change in mouse Y-position since last frame in screen space */
    val ydMouse: Float

    /** The change in horizontal scroll since last frame */
    val xScroll: Float

    /** The change in vertical scroll since last frame */
    val yScroll: Float

    /** The text written since last frame if focus is acquired, else empty string */
    val textInput: String

    /** The keys clicked since last frame if focus is acquired, else empty list */
    val clickedKeys: List<Key>

    /** All connected gamepads */
    val gamepads: List<Gamepad>

    /**
     * Returns true if the given keyboard [Key] is currently pressed and focus is acquired.
     */
    fun isPressed(key: Key): Boolean

    /**
     * Returns True if the given [MouseButton] is currently pressed and focus is acquired.
     */
    fun isPressed(btn: MouseButton): Boolean

    /**
     * Returns True if the given keyboard [Key] was pressed this frame and focus is acquired.
     */
    fun wasClicked(key: Key): Boolean

    /**
     * Returns True if the given [MouseButton] was pressed this frame and focus is acquired.
     */
    fun wasClicked(btn: MouseButton): Boolean

    /**
     * Returns True if the given keyboard [Key] was released this frame and focus is acquired.
     */
    fun wasReleased(key: Key): Boolean

    /**
     * Returns True if the given [MouseButton] was released this frame and focus is acquired.
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
     * Requests focus for the given [FocusArea] by registering it on this frames focus stack. Which area from the
     * stack getting selected for focus the next frame is determined by the order in which they were requested and
     * mouse preses within those areas. If this area is currently the focused one, the input module will transition
     * to a focused state where all key and mouse presses will be registered. Otherwise, no keyboard or mouse presses
     * will be visible.
     */
    fun requestFocus(focusArea: FocusArea)

    /**
     * Acquires focus for the given [FocusArea] by making it the currently focused one and transitions the input
     * module to a focused state where all key and mouse presses will be registered.
     */
    fun acquireFocus(focusArea: FocusArea)

    /**
     * Releases focus for the given [FocusArea] if it is the currently focused one and gives the previously
     * focused area back its focus.
     */
    fun releaseFocus(focusArea: FocusArea)

    /**
     * Returns true if the given [FocusArea] is the currently focused one.
     */
    fun hasFocus(focusArea: FocusArea): Boolean

    /**
     * Returns true if the mouse is currently hovering over the given [FocusArea] and it is not obstructed
     * by another area above it.
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
    fun createCursor(cursor: Cursor)
    fun deleteCursor(cursor: Cursor)
    fun getCursorsToLoad(): List<Cursor>
}