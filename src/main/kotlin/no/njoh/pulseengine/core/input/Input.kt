package no.njoh.pulseengine.core.input
import no.njoh.pulseengine.core.asset.types.Cursor
import no.njoh.pulseengine.core.shared.primitives.Subscription

interface Input
{
    val xMouse: Float
    val yMouse: Float
    val xWorldMouse: Float
    val yWorldMouse: Float
    val xdMouse: Float
    val ydMouse: Float
    val xScroll: Int
    val yScroll: Int
    val textInput: String
    val gamepads: List<Gamepad>

    fun isPressed(key: Key): Boolean
    fun isPressed(btn: Mouse): Boolean
    fun wasClicked(key: Key): Boolean
    fun wasClicked(btn: Mouse): Boolean
    fun wasReleased(key: Key): Boolean
    fun wasReleased(btn: Mouse): Boolean
    fun setClipboard(text: String)
    fun getClipboard(): String
    fun setOnKeyPressed(callback: (Key) -> Unit): Subscription
    fun requestFocus(focusArea: FocusArea)
    fun acquireFocus(focusArea: FocusArea)
    fun releaseFocus(focusArea: FocusArea)
    fun hasFocus(focusArea: FocusArea): Boolean
    fun hasHoverFocus(focusArea: FocusArea): Boolean
    fun setCursor(cursorType: CursorType)
}

interface InputInternal : Input
{
    override var xWorldMouse: Float
    override var yWorldMouse: Float

    fun init(windowHandle: Long)
    fun cleanUp()
    fun pollEvents()
    fun setOnFocusChanged(callback: (Boolean) -> Unit)
    fun loadCursors(loader: (String, String, Int, Int) -> Cursor)
}