package no.njoh.pulseengine.modules
import no.njoh.pulseengine.data.*
import no.njoh.pulseengine.data.CursorType.*
import no.njoh.pulseengine.data.assets.Cursor
import no.njoh.pulseengine.util.Logger
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer

interface Input
{
    val xMouse: Float
    val yMouse: Float
    val xWorldMouse: Float
    val yWorldMouse: Float
    val xdMouse: Float
    val ydMouse: Float
    val scroll: Int
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
    fun setCursor(cursorType: CursorType)
}

interface InputEngineInterface : Input
{
    override var xWorldMouse: Float
    override var yWorldMouse: Float

    fun init(windowHandle: Long)
    fun cleanUp()
    fun pollEvents()
    fun setOnFocusChanged(callback: (Boolean) -> Unit)
    fun loadCursors(loader: (String, String, Int, Int) -> Cursor)
}

class InputImpl : InputEngineInterface
{
    // Exposed properties
    override var xMouse = 0.0f
    override var yMouse = 0.0f
    override var xWorldMouse = 0f
    override var yWorldMouse = 0f
    override var scroll = 0
    override var gamepads = mutableListOf<Gamepad>()
    override var textInput: String = ""
    override val xdMouse: Float
        get() = xMouse - xMouseLast
    override val ydMouse: Float
        get() = yMouse - yMouseLast

    private var xMouseLast = 0.0f
    private var yMouseLast = 0.0f
    private var windowHandle: Long = -1
    private val clicked = ByteArray(Key.LAST.code + 1)
    private val onKeyPressedCallbacks = mutableListOf<(Key) -> Unit>()
    private var onFocusChangedCallback: (Boolean) -> Unit = {}
    private var focusStack = mutableListOf<FocusArea>()
    private var currentFocusArea: FocusArea? = null
    private var previousFocusArea: FocusArea? = null

    private var cursors = mutableMapOf<CursorType, Cursor>()
    private var selectedCursor = ARROW
    private var activeCursor = ARROW

    override fun init(windowHandle: Long)
    {
        Logger.info("Initializing input...")
        this.windowHandle = windowHandle

        glfwSetKeyCallback(windowHandle) { window, key, scancode, action, mods ->
            if (key >= 0)
            {
                clicked[key] = if (action == GLFW_PRESS || action == GLFW_REPEAT) 1 else -1
                if (action == GLFW_PRESS && onKeyPressedCallbacks.isNotEmpty())
                {
                    Key.values()
                        .find { it.code == key }
                        ?.let { keyEnum -> onKeyPressedCallbacks.forEach { it.invoke(keyEnum) } }
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
            scroll = yoffset.toInt()
        }

        glfwSetMouseButtonCallback(windowHandle) { window, button, action, mods ->
            clicked[button] = if (action == GLFW_PRESS) 1 else -1
            if (action == GLFW_PRESS && focusStack.isNotEmpty())
            {
                focusStack
                    .lastOrNull { it.isInside(xMouse, yMouse) }
                    ?.let { acquireFocus(it) }
            }
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
                ARROW -> Cursor.createWithHandle(glfwCreateStandardCursor(0x00036001))
                HAND -> Cursor.createWithHandle(glfwCreateStandardCursor(0x00036004))
                IBEAM -> Cursor.createWithHandle(glfwCreateStandardCursor(0x00036002))
                CROSSHAIR -> Cursor.createWithHandle(glfwCreateStandardCursor(0x00036003))
                HORIZONTAL_RESIZE -> Cursor.createWithHandle(glfwCreateStandardCursor(0x00036005))
                VERTICAL_RESIZE -> Cursor.createWithHandle(glfwCreateStandardCursor(0x00036006))
                MOVE -> loader.invoke("/pulseengine/cursors/move.png", "move_cursor", 8, 8)
                ROTATE -> loader.invoke("/pulseengine/cursors/rotate.png", "rotate_cursor", 6, 6)
                TOP_LEFT_RESIZE -> loader.invoke("/pulseengine/cursors/resize_top_left.png", "resize_top_left_cursor", 8, 8)
                TOP_RIGHT_RESIZE -> loader.invoke("/pulseengine/cursors/resize_top_right.png", "resize_top_right_cursor", 8, 8)
            }
        }
    }

    override fun isPressed(btn: Mouse): Boolean = glfwGetMouseButton(windowHandle, btn.code) == 1

    override fun isPressed(key: Key): Boolean = glfwGetKey(windowHandle, key.code) == 1

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
        if (focusArea !in focusStack)
            focusStack.add(focusArea)

        onFocusChangedCallback.invoke(hasFocus(focusArea))
    }

    override fun releaseFocus(focusArea: FocusArea)
    {
        if (currentFocusArea == focusArea)
        {
            currentFocusArea = previousFocusArea
            previousFocusArea = focusStack.firstOrNull()
        }
    }

    override fun hasFocus(focusArea: FocusArea): Boolean =
         focusArea == currentFocusArea

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
        scroll = 0
        textInput = ""
        clicked.fill(0)
        glfwPollEvents()
        gamepads.forEach { it.updateState() }
        focusStack.clear()
        updateSelectedCursor()
    }

    private fun updateSelectedCursor()
    {
        if (activeCursor != selectedCursor)
        {
            cursors[selectedCursor]
                ?.let { cursor ->
                    if (cursor.handle != -1L) glfwSetCursor(windowHandle, cursor.handle)
                    else Logger.error("Cursor of type: $selectedCursor has not been loaded")
                }
                ?: run { Logger.error("Cursor of type: $selectedCursor has not been registered in input module") }

            activeCursor = selectedCursor
        }
    }

    override fun cleanUp()
    {
        Logger.info("Cleaning up input...")
        glfwFreeCallbacks(windowHandle)
    }
}

data class Gamepad(var id: Int)
{
    private var axes: FloatBuffer = FloatBuffer.allocate(6)
    private var buttons: ByteBuffer = ByteBuffer.allocate(15)

    init { updateState() }

    fun isPressed(button: Button): Boolean = buttons[button.code] > 0
    fun getAxis(axis: Axis): Float = axes[axis.code]
    fun updateState()
    {
        glfwGetJoystickAxes(id)?.let { this.axes = it }
        glfwGetJoystickButtons(id)?.let { this.buttons = it }
    }
}

class IdleInput(private val activeInput: InputEngineInterface) : InputEngineInterface
{
    override var xWorldMouse = 0f
    override var yWorldMouse = 0f
    override val xMouse
        get() = activeInput.xMouse
    override val yMouse
        get() = activeInput.yMouse
    override val xdMouse = 0f
    override val ydMouse = 0f
    override val scroll = 0
    override val textInput: String = ""
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
    override fun loadCursors(callback: (String, String, Int, Int) -> Cursor) = activeInput.loadCursors(callback)
    override fun setOnKeyPressed(callback: (Key) -> Unit) = activeInput.setOnKeyPressed(callback)
    override fun requestFocus(focusArea: FocusArea) = activeInput.requestFocus(focusArea)
    override fun acquireFocus(focusArea: FocusArea) = activeInput.acquireFocus(focusArea)
    override fun releaseFocus(focusArea: FocusArea) = activeInput.releaseFocus(focusArea)
    override fun hasFocus(focusArea: FocusArea): Boolean = activeInput.hasFocus(focusArea)
    override fun setCursor(cursorType: CursorType) = activeInput.setCursor(cursorType)
}





