# LWJGL 3 + Kotlin

To build the jar:

    gradle build

To run it:

    java -jar build/libs/LWJGLKotlinExample-0.1-SNAPSHOT.jar

If you get this error:

    Exception in thread "main" java.lang.ExceptionInInitializerError
	at org.lwjgl.glfw.GLFW.glfwCreateWindow(GLFW.java:1248)
	at com.example.Engine.init(Engine.kt:40)
	at com.example.Engine.run(Engine.kt:113)
	at com.example.GameKt.main(Game.kt:7)
    Caused by: java.lang.IllegalStateException: GLFW windows may only be created on the main thread and that thread must be the first thread in the process. Please run the JVM with -XstartOnFirstThread. For offscreen rendering, make sure another window toolkit (e.g. AWT or JavaFX) is initialized before GLFW.
	at org.lwjgl.glfw.EventLoop$OffScreen.<clinit>(EventLoop.java:39)
	... 4 more

  ...re-run the app using the following command:

     java -XstartOnFirstThread -jar build/libs/LWJGLKotlinExample-0.1-SNAPSHOT.jar
