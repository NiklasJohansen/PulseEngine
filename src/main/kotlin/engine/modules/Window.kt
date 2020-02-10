package engine.modules

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil.NULL

// Exposed to game code
interface WindowInterface
{
    val width: Int
    val height: Int
    var title: String
}

// Exposed to game engine
interface WindowEngineInterface : WindowInterface
{
    fun init()
    fun cleanUp()
    fun setOnResizeEvent(callback: (width: Int, height: Int) -> Unit)
    fun swapBuffers()
    fun isOpen(): Boolean
    val windowHandle: Long
}

class Window : WindowEngineInterface
{
    // Exposed properties
    override var windowHandle : Long = -1
    override var width: Int = 1500
    override var height: Int = 1000
    override var title: String = ""
        set(value) {
            glfwSetWindowTitle(windowHandle, value)
            field = value
        }

    // Internal properties
    private var resizeCallBack: (width: Int, height: Int) -> Unit = {_,_ -> }

    override fun init()
    {
        println("Initializing Window...")

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

    override fun setOnResizeEvent(callback: (width: Int, height: Int) -> Unit) { resizeCallBack = callback }

    override fun swapBuffers() = glfwSwapBuffers(windowHandle)

    override fun isOpen(): Boolean = !glfwWindowShouldClose(windowHandle)

    override fun cleanUp()
    {
        println("Cleaning up window...")
        glfwSetErrorCallback(null)
        glfwDestroyWindow(windowHandle)
        glfwTerminate()
    }
}