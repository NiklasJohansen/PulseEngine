package no.njoh.pulseengine.core.input

import no.njoh.pulseengine.core.input.CursorType.*
import no.njoh.pulseengine.core.asset.types.Cursor
import no.njoh.pulseengine.core.asset.types.Cursor.Companion.createWithHandle
import no.njoh.pulseengine.core.console.Subscription
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.lastOrNullFast
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*

open class InputImpl : InputInternal
{
    // Exposed properties
    override var xMouse = 0.0f
    override var yMouse = 0.0f
    override var xWorldMouse = 0f
    override var yWorldMouse = 0f
    override var xScroll = 0f
    override var yScroll = 0f
    override var gamepads = mutableListOf<Gamepad>()
    override var textInput: String = ""
    override val clickedKeys = mutableListOf<Key>()
    override val xdMouse: Float
        get() = xMouse - xMouseLast
    override val ydMouse: Float
        get() = yMouse - yMouseLast

    private var xMouseLast = 0.0f
    private var yMouseLast = 0.0f
    private var windowHandle: Long = -1
    private val clicked = ByteArray(Key.LAST.code + 1)
    private val pressed = ByteArray(Key.LAST.code + 1)
    private val onKeyPressedCallbacks = mutableListOf<(Key) -> Unit>()
    private var onFocusChangedCallback: (Boolean) -> Unit = {}
    private var focusStack = mutableListOf<FocusArea>()
    private var currentFocusArea: FocusArea? = null
    private var previousFocusArea: FocusArea? = null
    private var hoverFocusArea: FocusArea? = null

    private var cursors = mutableMapOf<CursorType, Cursor>()
    private var selectedCursor = ARROW
    private var activeCursor = ARROW
    private var currentFrame = 0

    override fun init(windowHandle: Long)
    {
        Logger.info("Initializing input (${this::class.simpleName})")

        this.windowHandle = windowHandle

        glfwSetKeyCallback(windowHandle) { window, keyCode, scancode, action, mods ->
            if (keyCode >= 0)
            {
                clicked[keyCode] = if (action == GLFW_PRESS || action == GLFW_REPEAT) 1 else -1
                pressed[keyCode] = if (action == GLFW_PRESS || action == GLFW_REPEAT) 1 else 0
                if (action == GLFW_PRESS)
                {
                    Key.codes[keyCode]?.let { keyEnum ->
                        onKeyPressedCallbacks.forEachFast { it.invoke(keyEnum) }
                        clickedKeys.add(keyEnum)
                    }
                }
            }
        }

        glfwSetCharCallback(windowHandle) { window, character ->
            textInput += character.toChar()
        }

        glfwSetCursorPosCallback(windowHandle) { window, xPos, yPos ->
            xMouseLast = xMouse
            yMouseLast = yMouse
            xMouse = xPos.toFloat()
            yMouse = yPos.toFloat()
        }

        glfwSetScrollCallback(windowHandle) { window, xoffset, yoffset ->
            if (isPressed(Key.LEFT_SHIFT))
            {
                xScroll = yoffset.toFloat()
            }
            else
            {
                xScroll = xoffset.toFloat()
                yScroll = yoffset.toFloat()
            }
        }

        glfwSetMouseButtonCallback(windowHandle) { window, button, action, mods ->
            clicked[button] = if (action == GLFW_PRESS) 1 else -1
            pressed[button] = if (action == GLFW_PRESS) 1 else 0
            if (action == GLFW_PRESS && focusStack.isNotEmpty())
                focusStack.lastOrNullFast { it.isInside(xMouse, yMouse) }?.let { acquireFocus(it) }
        }

        glfwSetJoystickCallback { jid: Int, event: Int ->
            if (glfwJoystickIsGamepad(jid))
            {
                if (event == GLFW_CONNECTED)
                    gamepads.add(Gamepad(jid)).also { Logger.info("Added joystick: $jid") }
                else if (event == GLFW_DISCONNECTED)
                    gamepads.removeIf { it.id == jid }.also { Logger.info("Removed joystick: $jid") }
            }
        }

        gamepads = GLFW_JOYSTICK_1
            .until(GLFW_JOYSTICK_LAST)
            .filter { glfwJoystickPresent(it) && glfwJoystickIsGamepad(it) }
            .map { Gamepad(id = it) }
            .toMutableList()
    }

    override fun loadCursors(loader: (String, String, Int, Int) -> Cursor)
    {
        for (type in values())
        {
            cursors[type] = when (type)
            {
                ARROW -> createWithHandle(glfwCreateStandardCursor(0x00036001))
                HAND -> createWithHandle(glfwCreateStandardCursor(0x00036004))
                IBEAM -> createWithHandle(glfwCreateStandardCursor(0x00036002))
                CROSSHAIR -> createWithHandle(glfwCreateStandardCursor(0x00036003))
                HORIZONTAL_RESIZE -> createWithHandle(glfwCreateStandardCursor(0x00036005))
                VERTICAL_RESIZE -> createWithHandle(glfwCreateStandardCursor(0x00036006))
                MOVE -> loader.invoke("/pulseengine/cursors/move.png", "move_cursor", 8, 8)
                ROTATE -> loader.invoke("/pulseengine/cursors/rotate.png", "rotate_cursor", 6, 6)
                TOP_LEFT_RESIZE -> loader.invoke("/pulseengine/cursors/resize_top_left.png", "resize_top_left_cursor", 8, 8)
                TOP_RIGHT_RESIZE -> loader.invoke("/pulseengine/cursors/resize_top_right.png", "resize_top_right_cursor", 8, 8)
            }
        }
    }

    override fun isPressed(btn: Mouse): Boolean = pressed[btn.code] > 0

    override fun isPressed(key: Key): Boolean = pressed[key.code] > 0

    override fun wasClicked(key: Key): Boolean = clicked[key.code] > 0

    override fun wasClicked(btn: Mouse): Boolean = clicked[btn.code] > 0

    override fun wasReleased(key: Key): Boolean = clicked[key.code] < 0

    override fun wasReleased(btn: Mouse): Boolean = clicked[btn.code] < 0

    override fun getClipboard(): String = glfwGetClipboardString(windowHandle) ?: ""

    override fun setClipboard(text: String) = glfwSetClipboardString(windowHandle, text)

    override fun setOnKeyPressed(callback: (Key) -> Unit): Subscription
    {
        onKeyPressedCallbacks.add(callback)
        return object : Subscription
        {
            override fun unsubscribe()
            {
                onKeyPressedCallbacks.remove(callback)
            }
        }
    }

    override fun acquireFocus(focusArea: FocusArea)
    {
        if (focusArea != currentFocusArea)
        {
            previousFocusArea = currentFocusArea
            currentFocusArea = focusArea
        }

        onFocusChangedCallback.invoke(true)
    }

    override fun requestFocus(focusArea: FocusArea)
    {
        if (focusArea.frame != currentFrame)
        {
            focusStack.add(focusArea)
            focusArea.frame = currentFrame
        }

        onFocusChangedCallback.invoke(hasFocus(focusArea))
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

    override fun setCursor(cursorType: CursorType)
    {
        selectedCursor = cursorType
    }

    override fun setOnFocusChanged(callback: (Boolean) -> Unit)
    {
        this.onFocusChangedCallback = callback
    }

    override fun pollEvents()
    {
        xMouseLast = xMouse
        yMouseLast = yMouse
        xScroll = 0f
        yScroll = 0f
        textInput = ""
        clicked.fill(0)
        clickedKeys.clear()
        glfwPollEvents()
        gamepads.forEachFast { it.updateState() }
        if (focusStack.size == 1)
            currentFocusArea = focusStack.first()
        hoverFocusArea = focusStack.lastOrNullFast { it.isInside(xMouse, yMouse) }
        focusStack.clear()
        currentFrame++
        updateSelectedCursor()
    }

    private fun updateSelectedCursor()
    {
        if (activeCursor != selectedCursor)
        {
            cursors[selectedCursor]?.let { cursor ->
                if (cursor.handle != -1L) glfwSetCursor(windowHandle, cursor.handle)
                else Logger.error("Cursor of type: $selectedCursor has not been loaded")
            } ?: run {
                Logger.error("Cursor of type: $selectedCursor has not been registered in input module")
            }
            activeCursor = selectedCursor
        }
    }

    override fun cleanUp()
    {
        Logger.info("Cleaning up input (${this::class.simpleName})")
        glfwFreeCallbacks(windowHandle)
    }
}