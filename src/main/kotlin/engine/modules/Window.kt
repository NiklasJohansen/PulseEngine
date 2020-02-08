package engine.modules

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil.NULL

interface WindowInterface
{
    val width: Int
    val height: Int
    var title: String
}

class Window : WindowInterface
{
    override var width: Int = 1500
    override var height: Int = 1000
    override var title: String = ""
        set(value) {
            glfwSetWindowTitle(windowHandle, value)
            field = value
        }

    val windowHandle : Long
    var resizeCallBack: (width: Int, height: Int) -> Unit = {_,_ -> }

    init
    {
        if (!glfwInit())
            throw IllegalStateException("Unable to initialize GLFW")

        GLFWErrorCallback.createPrint(System.err).set()

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        
        windowHandle = glfwCreateWindow(width, height, "", NULL, NULL)
        if (windowHandle == NULL)
            throw RuntimeException("Failed to create the GLFW windowHandle")

        val videoMode = glfwGetVideoMode(glfwGetPrimaryMonitor())
        glfwSetWindowPos(
            windowHandle,
            (videoMode!!.width() - width) / 2,
            (videoMode.height() - height) / 2
        )

        glfwSetWindowSizeCallback(windowHandle, object : GLFWWindowSizeCallback()
        {
            override fun invoke(window: Long, width: Int, height: Int)
            {
                this@Window.width = width
                this@Window.height = height
                resizeCallBack(width, height)
            }
        })
        
        glfwMakeContextCurrent(windowHandle)
        glfwSwapInterval(0)
        glfwShowWindow(windowHandle)
    }

    fun setOnResizeEvent(callback: (width: Int, height: Int) -> Unit) { resizeCallBack = callback }

    fun swapBuffers() = glfwSwapBuffers(windowHandle)

    fun isOpen(): Boolean = !glfwWindowShouldClose(windowHandle)

    fun cleanUp()
    {
        println("Cleaning up window")
        glfwDestroyWindow(windowHandle)
    }
}