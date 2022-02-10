package no.njoh.pulseengine.modules.input

import org.lwjgl.glfw.GLFW.glfwGetJoystickAxes
import org.lwjgl.glfw.GLFW.glfwGetJoystickButtons
import java.nio.ByteBuffer
import java.nio.FloatBuffer

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