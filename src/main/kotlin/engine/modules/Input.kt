package engine.modules
import engine.data.Axis
import engine.data.Button
import engine.data.Key
import engine.data.Mouse
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer

interface InputInterface {
    val xMouse: Float
    val yMouse: Float
    val xdMouse: Float
    val ydMouse: Float
    val scroll: Int
    val gamepads: List<Gamepad>

    fun isPressed(key: Key): Boolean
    fun isPressed(btn: Mouse): Boolean
}

class Input(private val windowHandle: Long) : InputInterface
{
    override var xMouse = 0.0f
    override var yMouse = 0.0f
    override var scroll = 0
    override var gamepads = mutableListOf<Gamepad>()

    override val xdMouse: Float
        get() = xMouse - xMouseLast

    override val ydMouse: Float
        get() = yMouse - yMouseLast

    private var xMouseLast = 0.0f
    private var yMouseLast = 0.0f

    init
    {
        glfwSetKeyCallback(windowHandle) { window: Long, key: Int, scancode: Int, action: Int, mods: Int ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true)
        }

        glfwSetCursorPosCallback(windowHandle) { window: Long, xPos: Double, yPos: Double ->
            xMouseLast = xMouse
            yMouseLast = yMouse
            xMouse = xPos.toFloat()
            yMouse = yPos.toFloat()
        }

        glfwSetScrollCallback(windowHandle) { window: Long, xoffset: Double, yoffset: Double ->
            scroll = yoffset.toInt()
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

    fun pollEvents()
    {
        xMouseLast = xMouse
        yMouseLast = yMouse
        scroll = 0
        glfwPollEvents()
        gamepads.forEach { it.updateState() }
    }

    fun cleanUp()
    {
        println("Cleaning up input")
        glfwFreeCallbacks(windowHandle);
    }
}


data class Gamepad(var id: Int)
{
    private var axes: FloatBuffer = FloatBuffer.allocate(6)
    private var buttons: ByteBuffer = ByteBuffer.allocate(15)

    fun updateState()
    {
        glfwGetJoystickAxes(id)?.let { this.axes = it }
        glfwGetJoystickButtons(id)?.let { this.buttons = it }
    }

    fun isPressed(button: Button): Boolean = buttons[button.code] > 0
    fun getAxis(axis: Axis): Float = axes[axis.code]
}







