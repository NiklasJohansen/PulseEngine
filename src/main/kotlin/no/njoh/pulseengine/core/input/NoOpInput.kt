package no.njoh.pulseengine.core.input

import no.njoh.pulseengine.core.asset.types.Cursor
import no.njoh.pulseengine.core.console.Subscription
import no.njoh.pulseengine.core.shared.platform.PlatformEvent
import no.njoh.pulseengine.core.shared.platform.PlatformEventBuffer

class NoOpInput : InputInternal
{
    override val clickedKeys = emptyList<Key>()
    override val gamepads = emptyList<Gamepad>()
    override val textInput = ""
    override val xMouse = 0f
    override val yMouse = 0f
    override var xWorldMouse = 0f
    override var yWorldMouse = 0f
    override val xdMouse = 0f
    override val ydMouse = 0f
    override val xScroll = 0f
    override val yScroll = 0f
    override fun acquireFocus(focusArea: FocusArea) {}
    override fun createCursor(cursor: Cursor) {}
    override fun deleteCursor(cursor: Cursor) {}
    override fun destroy() {}
    override fun pollOutgoingPlatformEvents(buffer: PlatformEventBuffer) {}
    override fun handleIncomingPlatformEvents(buffer: PlatformEventBuffer) {}
    override fun getClipboard(callback: (String) -> Unit) {}
    override fun getDefaultCursorToLoad() = emptyList<Cursor>()
    override fun hasFocus(focusArea: FocusArea) = false
    override fun hasHoverFocus(focusArea: FocusArea) = false
    override fun init(cursorPosScale: Float) {}
    override fun isPressed(key: Key) = false
    override fun isPressed(btn: MouseButton) = false
    override fun releaseFocus(focusArea: FocusArea) {}
    override fun requestFocus(focusArea: FocusArea) {}
    override fun setClipboard(content: String) {}
    override fun setCursorMode(cursorMode: CursorMode) {}
    override fun setCursorPosition(x: Float, y: Float) {}
    override fun setCursorType(cursorType: CursorType) {}
    override fun setOnKeyPressed(callback: (Key) -> Unit) = Subscription {}
    override fun wasClicked(key: Key) = false
    override fun wasClicked(btn: MouseButton) = false
    override fun wasReleased(key: Key) = false
    override fun wasReleased(btn: MouseButton) = false
}