package no.njoh.pulseengine.core.input

import gnu.trove.map.hash.THashMap
import no.njoh.pulseengine.core.input.CursorType.*
import no.njoh.pulseengine.core.asset.types.Cursor
import no.njoh.pulseengine.core.console.Subscription
import no.njoh.pulseengine.core.input.CursorMode.*
import no.njoh.pulseengine.core.shared.platform.*
import no.njoh.pulseengine.core.shared.platform.KeyEvent.KeyAction
import no.njoh.pulseengine.core.shared.platform.MouseButtonEvent.*
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.lastOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import no.njoh.pulseengine.core.shared.utils.Logger

open class InputImpl : InputInternal
{
    override val xdMouse get() = (xMouse - xMouseLast)
    override val ydMouse get() = (yMouse - yMouseLast)

    override var textInput: String = ""
        get() = if (isFocused) field else ""

    override val clickedKeys = mutableListOf<Key>()
        get() = if (isFocused) field else NO_KEYS

    override var xMouse            = 0f
    override var yMouse            = 0f
    override var xWorldMouse       = 0f
    override var yWorldMouse       = 0f
    override var xScroll           = 0f
    override var yScroll           = 0f
    override var gamepads          = mutableListOf<Gamepad>()

    private var xMouseLast         = 0f
    private var yMouseLast         = 0f
    private var cursorPosScale     = 1f
    private val clicked            = ByteArray(Key.LAST.code + 1)
    private val pressed            = ByteArray(Key.LAST.code + 1)
    private val onKeyPressed       = mutableListOf<(Key) -> Unit>()

    private var currentFrame       = 0
    private var isFocused          = true
    private var focusStack         = mutableListOf<FocusArea>()
    private var currentFocusArea   = null as FocusArea?
    private var previousFocusArea  = null as FocusArea?
    private var hoverFocusArea     = null as FocusArea?

    private var cursors            = THashMap<CursorType, Cursor>()
    private var selectedCursorType = ARROW
    private var activeCursorType   = ARROW
    private var selectedCursorMode = NORMAL
    private var activeCursorMode   = NORMAL

    private var onGetClipboard = mutableListOf<(String) -> Unit>()
    private var outgoingPlatformEvents = mutableListOf<PlatformEvent>()

    override fun init(cursorPosScale: Float)
    {
        Logger.info { "Initializing input (InputImpl)" }
        this.cursorPosScale = cursorPosScale
    }

    override fun isPressed(btn: MouseButton) =
        isFocused && pressed[btn.code] == PRESSED

    override fun isPressed(key: Key) =
        isFocused && pressed[key.code] == PRESSED

    override fun wasClicked(btn: MouseButton) =
        isFocused && clicked[btn.code] == PRESSED

    override fun wasClicked(key: Key) =
        isFocused && clicked[key.code] == PRESSED

    override fun wasReleased(btn: MouseButton) =
        isFocused && clicked[btn.code] == RELEASED

    override fun wasReleased(key: Key) =
        isFocused && clicked[key.code] == RELEASED

    override fun getClipboard(callback: (String) -> Unit)
    {
        onGetClipboard.add(callback)
        outgoingPlatformEvents += ClipboardRequestUpdateEvent()
    }

    override fun setClipboard(content: String)
    {
        outgoingPlatformEvents += ClipboardUpdateEvent(content)
    }

    override fun setOnKeyPressed(callback: (Key) -> Unit): Subscription
    {
        onKeyPressed.add(callback)
        return Subscription { /* onUnsubscribe */ onKeyPressed.remove(callback)  }
    }

    override fun acquireFocus(focusArea: FocusArea)
    {
        if (focusArea != currentFocusArea)
        {
            previousFocusArea = currentFocusArea
            currentFocusArea = focusArea
        }
        isFocused = true
    }

    override fun requestFocus(focusArea: FocusArea)
    {
        if (focusArea.frame != currentFrame)
        {
            focusStack.add(focusArea)
            focusArea.frame = currentFrame
        }
        isFocused = hasFocus(focusArea)
    }

    override fun releaseFocus(focusArea: FocusArea)
    {
        if (currentFocusArea === focusArea)
        {
            currentFocusArea = previousFocusArea
            previousFocusArea = focusStack.firstOrNull()
        }
    }

    override fun hasFocus(focusArea: FocusArea): Boolean =
         focusArea === currentFocusArea

    override fun hasHoverFocus(focusArea: FocusArea): Boolean =
        focusArea === hoverFocusArea

    override fun setCursorType(cursorType: CursorType)
    {
        selectedCursorType = cursorType
    }

    override fun setCursorMode(cursorMode: CursorMode)
    {
        selectedCursorMode = cursorMode
    }

    override fun setCursorPosition(x: Float, y: Float)
    {
        outgoingPlatformEvents += CursorSetPosEvent(x, y)
    }

    override fun pollOutgoingPlatformEvents(buffer: PlatformEventBuffer)
    {
        if (activeCursorType != selectedCursorType)
        {
            cursors[selectedCursorType]?.let { cursor ->
                if (cursor.handle != -1L) outgoingPlatformEvents += CursorSetEvent(cursor.handle)
                else Logger.error { "Cursor of type: $selectedCursorType has not been loaded" }
            } ?: run {
                Logger.error { "Cursor of type: $selectedCursorType has not been registered in input module" }
            }
            activeCursorType = selectedCursorType
        }

        if (activeCursorMode != selectedCursorMode)
        {
            outgoingPlatformEvents += CursorModeEvent(selectedCursorMode)
            activeCursorMode = selectedCursorMode
        }

        outgoingPlatformEvents.forEachFast { buffer.add(it) }
        outgoingPlatformEvents.clear()
    }

    override fun handleIncomingPlatformEvents(buffer: PlatformEventBuffer)
    {
        // Reset
        isFocused = true
        xMouseLast = xMouse
        yMouseLast = yMouse
        xScroll = 0f
        yScroll = 0f
        textInput = ""
        clicked.fill(UNCHANGED)
        clickedKeys.clear()

        // Poll all incoming platform events
        buffer.forEachEvent { handleEvent(it) }

        if (focusStack.size == 1)
            currentFocusArea = focusStack.first()
        hoverFocusArea = focusStack.lastOrNullFast { it.isInside(xMouse, yMouse) }
        focusStack.clear()
        currentFrame++
    }

    private fun handleEvent(e: PlatformEvent)
    {
        when (e)
        {
            is KeyEvent ->
            {
                if (e.keyCode < 0) return
                
                clicked[e.keyCode] = if (e.action == KeyAction.PRESSED || e.action == KeyAction.REPEAT) PRESSED else RELEASED
                pressed[e.keyCode] = if (e.action == KeyAction.PRESSED || e.action == KeyAction.REPEAT) PRESSED else UNCHANGED
                if (e.action == KeyAction.PRESSED)
                {
                    Key.codes[e.keyCode]?.let { keyEnum ->
                        onKeyPressed.forEachFast { it.invoke(keyEnum) }
                        clickedKeys.add(keyEnum)
                    }
                }
            }
            is CharacterEvent ->
            {
                textInput += e.character
            }
            is MouseMoveEvent ->
            {
                xMouseLast = xMouse
                yMouseLast = yMouse
                xMouse = e.x * cursorPosScale
                yMouse = e.y * cursorPosScale
            }
            is MouseButtonEvent ->
            {
                clicked[e.button] = if (e.action == MouseAction.PRESSED) PRESSED else RELEASED
                pressed[e.button] = if (e.action == MouseAction.PRESSED) PRESSED else UNCHANGED
                if (e.action == MouseAction.PRESSED && focusStack.isNotEmpty())
                {
                    focusStack.lastOrNullFast { it.isInside(xMouse, yMouse) }?.let { acquireFocus(it) }
                }
            }
            is ScrollEvent ->
            {
                if (isPressed(Key.LEFT_SHIFT))
                {
                    xScroll = e.yOffset
                }
                else
                {
                    xScroll = e.xOffset
                    yScroll = e.yOffset
                }
            }
            is GamepadConnectionEvent ->
            {
                if (e.connected)
                    gamepads += Gamepad(e.id).also { Logger.info { "Added joystick: ${e.id}" } }
                else
                    gamepads.removeWhen { it.id == e.id }.also { Logger.info { "Removed joystick: ${e.id}" } }
            }
            is GamepadUpdateEvent -> 
            {
                gamepads.find { it.id == e.id }?.updateState(e.axes, e.buttons)
            }
            is ClipboardUpdateEvent ->
            {
                onGetClipboard.forEachFast { it(e.content) }
                onGetClipboard.clear()
            }
        }
    }

    override fun getDefaultCursorToLoad() = listOf(
        Cursor("", "arrow_cursor", ARROW, 0, 0, 0x00036001),
        Cursor("", "hand_cursor", HAND, 0, 0, 0x00036004),
        Cursor("", "ibeam_cursor", IBEAM, 0, 0, 0x00036002),
        Cursor("", "crosshair_cursor", CROSSHAIR, 0, 0, 0x00036003),
        Cursor("", "horizontal_resize_cursor", HORIZONTAL_RESIZE, 0, 0, 0x00036005),
        Cursor("", "vertical_resize_cursor", VERTICAL_RESIZE, 0, 0, 0x00036006),
        Cursor("/pulseengine/cursors/move.png", "move_cursor", MOVE, 8, 8),
        Cursor("/pulseengine/cursors/rotate.png", "rotate_cursor", ROTATE, 6, 6),
        Cursor("/pulseengine/cursors/hand_grab.png", "hand_grab", HAND_GRAB, 8, 8),
        Cursor("/pulseengine/cursors/resize_top_left.png", "top_left_resize_cursor", TOP_LEFT_RESIZE, 8, 8),
        Cursor("/pulseengine/cursors/resize_top_right.png", "top_right_resize_tcursor", TOP_RIGHT_RESIZE, 8, 8)
    )

    override fun createCursor(cursor: Cursor)
    {
        outgoingPlatformEvents += CursorCreateEvent(cursor)
        cursors[cursor.type] = cursor
    }

    override fun deleteCursor(cursor: Cursor)
    {
        outgoingPlatformEvents += CursorDestroyEvent(cursor.handle)
    }

    override fun destroy()
    {
        Logger.info { "Destroying input (${this::class.simpleName})" }
    }

    companion object
    {
        private const val PRESSED: Byte = 1
        private const val RELEASED: Byte = -1
        private const val UNCHANGED: Byte = 0
        private val NO_KEYS = mutableListOf<Key>()
    }
}