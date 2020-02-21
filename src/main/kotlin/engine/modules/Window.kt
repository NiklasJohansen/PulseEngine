package engine.modules

import engine.data.ScreenMode.*
import engine.data.ScreenMode
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil.NULL

// Exposed to game code
interface WindowInterface
{
    fun updateScreenMode(mode: ScreenMode)
    val screenMode: ScreenMode
    val width: Int
    val height: Int
    var title: String
}

// Exposed to game engine
interface WindowEngineInterface : WindowInterface
{
    fun init(initWidth: Int, initHeight: Int, screenMode: ScreenMode)
    fun cleanUp()
    fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit)
    fun swapBuffers()
    fun isOpen(): Boolean
    val windowHandle: Long
}

class Window : WindowEngineInterface
{
    // Exposed properties
    override var windowHandle : Long = NULL
    override var screenMode: ScreenMode = WINDOWED
    override var width: Int = 800
    override var height: Int = 600
    override var title: String = ""
        set(value) {
            glfwSetWindowTitle(windowHandle, value)
            field = value
        }

    // Internal properties
    private var resizeCallBack: (width: Int, height: Int, windowRecreated: Boolean) -> Unit = { _, _, _ -> }
    private var windowedWidth: Int = 800
    private var windowedHeight: Int = 600

    override fun init(initWidth: Int, initHeight: Int, screenMode: ScreenMode)
    {
        println("Initializing Window...")
        if (!glfwInit())
            throw IllegalStateException("Unable to initialize GLFW")

        GLFWErrorCallback.createPrint(System.err).set()
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        this.screenMode = screenMode
        this.windowedWidth = initWidth
        this.windowedHeight = initHeight
        this.width = initWidth
        this.height = initHeight

        val monitor = if(screenMode == FULLSCREEN)
        {
            val primaryMonitor = glfwGetPrimaryMonitor()
            val videoMode = glfwGetVideoMode(primaryMonitor)
            width = videoMode?.width() ?: width
            height = videoMode?.height() ?: height
            primaryMonitor
        }
        else NULL

        val newWindowHandle = glfwCreateWindow(width, height, title, monitor, windowHandle)
        if (newWindowHandle == NULL)
            throw RuntimeException("Failed to create the GLFW windowHandle")

        // Destroy previous window if it existed
        if(windowHandle != NULL)
            glfwDestroyWindow(windowHandle)

        if(screenMode == WINDOWED)
        {
            val mode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!
            glfwSetWindowPos(newWindowHandle, (mode.width() - width) / 2, (mode.height() - height) / 2)
        }

        glfwSetWindowSizeCallback(newWindowHandle, object : GLFWWindowSizeCallback()
        {
            override fun invoke(window: Long, width: Int, height: Int)
            {
                this@Window.width = width
                this@Window.height = height
                resizeCallBack(width, height, false)
            }
        })

        glfwMakeContextCurrent(newWindowHandle)
        glfwSwapInterval(0)
        glfwShowWindow(newWindowHandle)
        this.windowHandle = newWindowHandle
    }

    override fun updateScreenMode(mode: ScreenMode)
    {
        if(mode == this.screenMode)
            return

        // Create new window
        init(windowedWidth, windowedHeight, mode)

        // Notify observers
        resizeCallBack(width, height, true)
    }

    override fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit) { resizeCallBack = callback }

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