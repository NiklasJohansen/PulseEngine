package engine.modules
import engine.data.Axis
import engine.data.Button
import engine.data.Key
import engine.data.Mouse
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer

// Exposed to game code
interface InputInterface
{
    val xMouse: Float
    val yMouse: Float
    val xWorldMouse: Float
    val yWorldMouse: Float
    val xdMouse: Float
    val ydMouse: Float
    val scroll: Int
    val gamepads: List<Gamepad>

    fun isPressed(key: Key): Boolean
    fun isPressed(btn: Mouse): Boolean
    fun wasClicked(key: Key): Boolean
    fun wasClicked(btn: Mouse): Boolean
    fun wasReleased(key: Key): Boolean
    fun wasReleased(btn: Mouse): Boolean
}

// Exposed to game engine
interface InputEngineInterface : InputInterface
{
    override var xWorldMouse: Float
    override var yWorldMouse: Float

    fun init(windowHandle: Long)
    fun cleanUp()
    fun pollEvents()
}

class Input : InputEngineInterface
{
    // Exposed properties
    override var xMouse = 0.0f
    override var yMouse = 0.0f
    override var xWorldMouse = 0f
    override var yWorldMouse = 0f
    override var scroll = 0
    override var gamepads = mutableListOf<Gamepad>()
    override val xdMouse: Float
        get() = xMouse - xMouseLast
    override val ydMouse: Float
        get() = yMouse - yMouseLast

    // Internal properties
    private var xMouseLast = 0.0f
    private var yMouseLast = 0.0f
    private var windowHandle: Long = -1
    private val clicked = ByteArray(Key.LAST.code)

    override fun init(windowHandle: Long)
    {
        println("Initializing input...")
        this.windowHandle = windowHandle

        glfwSetKeyCallback(windowHandle) { window, key, scancode, action, mods ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true)
            if(key >= 0)
                clicked[key] = if(action == GLFW_PRESS) 1 else -1
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
            clicked[button] = if(action == GLFW_PRESS) 1 else -1
        }

        glfwSetJoystickCallback { jid: Int, event: Int ->
            if (glfwJoystickIsGamepad(jid))
            {
                if (event == GLFW_CONNECTED)
                    gamepads.add(Gamepad(jid)).also { println("Added joystick: $jid") }
                else if(event == GLFW_DISCONNECTED)
                    gamepads.removeIf { it.id == jid }.also { println("Removed joystick: $jid") }
            }
        }

        gamepads = GLFW_JOYSTICK_1
            .until(GLFW_JOYSTICK_LAST)
            .filter { glfwJoystickPresent(it) && glfwJoystickIsGamepad(it) }
            .map { Gamepad(id = it) }
            .toMutableList()
    }

    override fun isPressed(btn: Mouse): Boolean = glfwGetMouseButton(windowHandle, btn.code) == 1

    override fun isPressed(key: Key): Boolean = glfwGetKey(windowHandle, key.code) == 1

    override fun wasClicked(key: Key): Boolean = clicked[key.code] > 0

    override fun wasClicked(btn: Mouse): Boolean = clicked[btn.code] > 0

    override fun wasReleased(key: Key): Boolean = clicked[key.code] < 0

    override fun wasReleased(btn: Mouse): Boolean = clicked[btn.code] < 0

    override fun pollEvents()
    {
        xMouseLast = xMouse
        yMouseLast = yMouse
        scroll = 0
        clicked.fill(0)
        glfwPollEvents()
        gamepads.forEach { it.updateState() }
    }

    override fun cleanUp()
    {
        println("Cleaning up input...")
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







