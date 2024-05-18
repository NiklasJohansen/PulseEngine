package no.njoh.pulseengine.core.input

import no.njoh.pulseengine.core.input.GamepadAxis.*
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.glfwGetJoystickAxes
import org.lwjgl.glfw.GLFW.glfwGetJoystickButtons
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Gamepad(var id: Int)
{
    private var axes: FloatBuffer = FloatBuffer.allocate(6)
    private var buttons: ByteBuffer = ByteBuffer.allocate(15)
    private var leftStick = Vector2f(0f, 0f)
    private var rightStick = Vector2f(0f, 0f)

    init { updateState() }

    fun isPressed(button: GamepadButton): Boolean = buttons[button.code] > 0

    fun getAxis(axis: GamepadAxis): Float = axes[axis.code]

    fun getLeftStick(deadZone: Float = 0.2f): Vector2f =
        leftStick.set(getAxis(LEFT_X), getAxis(LEFT_Y)).filtered(deadZone)

    fun getRightStick(deadZone: Float = 0.2f): Vector2f =
        rightStick.set(getAxis(RIGHT_X), getAxis(RIGHT_Y)).filtered(deadZone)

    fun updateState()
    {
        glfwGetJoystickAxes(id)?.let { this.axes = it }
        glfwGetJoystickButtons(id)?.let { this.buttons = it }
    }

    private fun Vector2f.filtered(deadZone: Float): Vector2f
    {
        val length = sqrt(x * x + y * y)
        return if (length >= deadZone)
        {
            val angle = atan2(y, x)
            val scaledLength = (length - deadZone) / (1f - deadZone)
            this.set(cos(angle) * scaledLength, -sin(angle) * scaledLength)
        }
        else this.set(0f, 0f)
    }
}