package no.njoh.pulseengine.core.input

import no.njoh.pulseengine.core.input.CursorType.*
import no.njoh.pulseengine.core.asset.types.Cursor
import no.njoh.pulseengine.core.console.Subscription
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.lastOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWImage

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
    private var selectedCursorType = ARROW
    private var activeCursorType = ARROW
    private var selectedCursorMode = CursorMode.NORMAL
    private var activeCursorMode = CursorMode.NORMAL
    private var currentFrame = 0

    override fun init(windowHandle: Long)
    {
        Logger.info("Initializing input (${this::class.simpleName})")

        this.windowHandle = windowHandle

        glfwSetKeyCallback(windowHandle) { _, keyCode, _, action, _ ->
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

        glfwSetCharCallback(windowHandle) { _, character ->
            textInput += character.toChar()
        }

        glfwSetCursorPosCallback(windowHandle) { _, xPos, yPos ->
            xMouseLast = xMouse
            yMouseLast = yMouse
            xMouse = xPos.toFloat()
            yMouse = yPos.toFloat()
        }

        glfwSetScrollCallback(windowHandle) { _, xOffset, yOffset ->
            if (isPressed(Key.LEFT_SHIFT))
            {
                xScroll = yOffset.toFloat()
            }
            else
            {
                xScroll = xOffset.toFloat()
                yScroll = yOffset.toFloat()
            }
        }

        glfwSetMouseButtonCallback(windowHandle) { _, button, action, _ ->
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
                    gamepads.removeWhen { it.id == jid }.also { Logger.info("Removed joystick: $jid") }
            }
        }

        gamepads = GLFW_JOYSTICK_1
            .until(GLFW_JOYSTICK_LAST)
            .filter { glfwJoystickPresent(it) && glfwJoystickIsGamepad(it) }
            .map { Gamepad(id = it) }
            .toMutableList()

        // Create cursors from built in shapes
        cursors[ARROW]             = createBuiltInCursor(ARROW,0x00036001)
        cursors[HAND]              = createBuiltInCursor(HAND,0x00036004)
        cursors[IBEAM]             = createBuiltInCursor(IBEAM,0x00036002)
        cursors[CROSSHAIR]         = createBuiltInCursor(CROSSHAIR,0x00036003)
        cursors[HORIZONTAL_RESIZE] = createBuiltInCursor(HORIZONTAL_RESIZE,0x00036005)
        cursors[VERTICAL_RESIZE]   = createBuiltInCursor(VERTICAL_RESIZE,0x00036006)
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
        glfwSetCursorPos(windowHandle, x.toDouble(), y.toDouble())
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
        updateCursor()
    }

    private fun updateCursor()
    {
        if (activeCursorType != selectedCursorType)
        {
            cursors[selectedCursorType]?.let { cursor ->
                if (cursor.handle != -1L) glfwSetCursor(windowHandle, cursor.handle)
                else Logger.error("Cursor of type: $selectedCursorType has not been loaded")
            } ?: run {
                Logger.error("Cursor of type: $selectedCursorType has not been registered in input module")
            }
            activeCursorType = selectedCursorType
        }

        if (activeCursorMode != selectedCursorMode)
        {
            val glfwMode = when (selectedCursorMode)
            {
                CursorMode.NORMAL -> GLFW_CURSOR_NORMAL
                CursorMode.HIDDEN -> GLFW_CURSOR_HIDDEN
                CursorMode.GRABBED -> GLFW_CURSOR_DISABLED
            }
            glfwSetInputMode(windowHandle, GLFW_CURSOR, glfwMode)
            activeCursorMode = selectedCursorMode
        }
    }

    override fun getCursorsToLoad() = listOf(
        Cursor("/pulseengine/cursors/move.png", "move_cursor", MOVE, 8, 8),
        Cursor("/pulseengine/cursors/rotate.png", "rotate_cursor", ROTATE, 6, 6),
        Cursor("/pulseengine/cursors/resize_top_left.png", "top_left_resize_cursor", TOP_LEFT_RESIZE, 8, 8),
        Cursor("/pulseengine/cursors/resize_top_right.png", "top_right_resize_tcursor", TOP_RIGHT_RESIZE, 8, 8)
    )

    override fun createCursor(cursor: Cursor)
    {
        val cursorImg = GLFWImage.create()
        cursorImg.width(cursor.width)
        cursorImg.height(cursor.height)
        cursorImg.pixels(cursor.pixelBuffer!!)
        val handle = glfwCreateCursor(cursorImg, cursor.xHotspot, cursor.yHotspot)
        cursor.finalize(handle)
        cursors[cursor.type] = cursor
    }

    override fun deleteCursor(cursor: Cursor)
    {
        glfwDestroyCursor(cursor.handle)
    }

    override fun cleanUp()
    {
        Logger.info("Cleaning up input (${this::class.simpleName})")
        glfwFreeCallbacks(windowHandle)
    }

    private fun createBuiltInCursor(type: CursorType, shape: Int): Cursor =
        Cursor("", "standard_cursor", type, 0, 0).apply { finalize(handle = glfwCreateStandardCursor(shape)) }
}