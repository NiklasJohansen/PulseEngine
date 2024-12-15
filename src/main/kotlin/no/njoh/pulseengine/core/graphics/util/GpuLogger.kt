package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.shared.utils.LogLevel
import no.njoh.pulseengine.core.shared.utils.LogLevel.*
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.ARBDebugOutput
import org.lwjgl.opengl.ARBDebugOutput.glDebugMessageCallbackARB
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL30.GL_CONTEXT_FLAGS
import org.lwjgl.opengl.GLDebugMessageARBCallback
import org.lwjgl.opengl.GLDebugMessageCallback
import org.lwjgl.opengl.KHRDebug
import org.lwjgl.opengl.KHRDebug.GL_CONTEXT_FLAG_DEBUG_BIT
import org.lwjgl.opengl.KHRDebug.GL_DEBUG_OUTPUT

/**
 * Utility class for logging GPU debug messages.
 * Gives more readable RenderDoc captures and is useful for debugging OpenGL errors.
 */
object GpuLogger
{
    private var logLevel = INFO
    private var loggingEnabled = false

    fun setLogLevel(level: LogLevel)
    {
        logLevel = level

        if (level != OFF && !loggingEnabled)
        {
            enableLogging()
        }
        else if (level == OFF && loggingEnabled)
        {
            disableLogging()
        }
    }

    fun beginGroup(label: CharSequence)
    {
        if (logLevel != OFF) KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, 0, label)
    }

    fun endGroup()
    {
        if (logLevel != OFF) KHRDebug.glPopDebugGroup()
    }

    private fun enableLogging()
    {
        val caps = GL.getCapabilities()
        if (caps.GL_KHR_debug)
        {
            Logger.debug("Using KHR_debug for OpenGL logging")
            val callback = GLDebugMessageCallback.create { source, type, id, severity, length, message, userParam ->
                val type = KHR.typeMapping[type]
                val level = KHR.getLoglevel(severity, type)
                if (level.value >= logLevel.value && source != KHRDebug.GL_DEBUG_SOURCE_APPLICATION)
                {
                    val message = GLDebugMessageCallback.getMessage(length, message)
                    val typeName = type?.name ?: "UNKNOWN TYPE"
                    val source = KHR.sourceMapping[source] ?: "UNKNOWN SOURCE"
                    Logger.log(level) { "[OPENGL] [" plus source plus "] [" plus typeName plus "] " plus message }
                }
            }
            KHRDebug.glDebugMessageCallback(callback, 0L)
        }
        else if (caps.GL_ARB_debug_output)
        {
            Logger.debug("Using ARB_debug_output for OpenGL logging")
            val callback = GLDebugMessageARBCallback.create { source, type, id, severity, length, message, userParam ->
                val type = ARB.typeMapping[type]
                val level = ARB.getLoglevel(severity, type)
                if (level.value >= logLevel.value && source != ARBDebugOutput.GL_DEBUG_SOURCE_APPLICATION_ARB)
                {
                    val message = GLDebugMessageARBCallback.getMessage(length, message)
                    val typeName = type?.name ?: "UNKNOWN TYPE"
                    val source = ARB.sourceMapping[source] ?: "UNKNOWN SOURCE"
                    Logger.log(level) { "[OPENGL] [" plus source plus "] [" plus typeName plus "] " plus message }
                }
            }
            glDebugMessageCallbackARB(callback, 0L)
        }

        // Enable debug output if not already enabled.
        // Should be enabled by default when buildType is DEBUG and GLFW_OPENGL_DEBUG_CONTEXT is set GLFW_TRUE
        if (caps.OpenGL30 && (glGetInteger(GL_CONTEXT_FLAGS) and GL_CONTEXT_FLAG_DEBUG_BIT) == 0)
        {
            Logger.warn("Current OpenGL context does not have the debug flag enabled. Enabling: GL_DEBUG_OUTPUT")
            glEnable(GL_DEBUG_OUTPUT)
        }

        loggingEnabled = true
    }

    private fun disableLogging()
    {
        val caps = GL.getCapabilities()
        if (caps.GL_KHR_debug)
        {
            KHRDebug.glDebugMessageCallback(null, 0L)
        }
        else if (caps.GL_ARB_debug_output)
        {
            glDebugMessageCallbackARB(null, 0L)
        }
        glDisable(GL_DEBUG_OUTPUT)
        loggingEnabled = false
    }

    private object KHR
    {
        fun getLoglevel(severity: Int, type: Type?) = LogLevel.maxOf(severityMapping[severity] ?: DEBUG, type?.logLevel ?: DEBUG)

        val severityMapping = mapOf(
            KHRDebug.GL_DEBUG_SEVERITY_HIGH         to WARN,
            KHRDebug.GL_DEBUG_SEVERITY_MEDIUM       to WARN,
            KHRDebug.GL_DEBUG_SEVERITY_LOW          to INFO,
            KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION to DEBUG
        )

        val sourceMapping = mapOf(
            KHRDebug.GL_DEBUG_SOURCE_API             to "API",
            KHRDebug.GL_DEBUG_SOURCE_WINDOW_SYSTEM   to "WINDOW SYSTEM",
            KHRDebug.GL_DEBUG_SOURCE_SHADER_COMPILER to "SHADER COMPILER",
            KHRDebug.GL_DEBUG_SOURCE_THIRD_PARTY     to "THIRD PARTY",
            KHRDebug.GL_DEBUG_SOURCE_APPLICATION     to "APPLICATION",
            KHRDebug.GL_DEBUG_SOURCE_OTHER           to "OTHER"
        )

        val typeMapping = mapOf(
            KHRDebug.GL_DEBUG_TYPE_ERROR               to Type(ERROR, "ERROR"),
            KHRDebug.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR  to Type(ERROR, "UNDEFINED BEHAVIOR"),
            KHRDebug.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR to Type(WARN,  "DEPRECATED BEHAVIOR"),
            KHRDebug.GL_DEBUG_TYPE_PORTABILITY         to Type(INFO,  "PORTABILITY"),
            KHRDebug.GL_DEBUG_TYPE_PERFORMANCE         to Type(INFO,  "PERFORMANCE"),
            KHRDebug.GL_DEBUG_TYPE_MARKER              to Type(DEBUG, "MARKER"),
            KHRDebug.GL_DEBUG_TYPE_OTHER               to Type(DEBUG, "OTHER")
        )
    }

    private object ARB
    {
        fun getLoglevel(severity: Int, type: Type?) = LogLevel.maxOf(severityMapping[severity] ?: DEBUG, type?.logLevel ?: DEBUG)

        val severityMapping = mapOf(
            ARBDebugOutput.GL_DEBUG_SEVERITY_HIGH_ARB   to WARN,
            ARBDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_ARB to WARN,
            ARBDebugOutput.GL_DEBUG_SEVERITY_LOW_ARB    to INFO
        )

        val sourceMapping = mapOf(
            ARBDebugOutput.GL_DEBUG_SOURCE_API_ARB             to "API",
            ARBDebugOutput.GL_DEBUG_SOURCE_WINDOW_SYSTEM_ARB   to "WINDOW SYSTEM",
            ARBDebugOutput.GL_DEBUG_SOURCE_SHADER_COMPILER_ARB to "SHADER COMPILER",
            ARBDebugOutput.GL_DEBUG_SOURCE_THIRD_PARTY_ARB     to "THIRD PARTY",
            ARBDebugOutput.GL_DEBUG_SOURCE_APPLICATION_ARB     to "APPLICATION",
            ARBDebugOutput.GL_DEBUG_SOURCE_OTHER_ARB           to "OTHER"
        )

        val typeMapping = mapOf(
            ARBDebugOutput.GL_DEBUG_TYPE_ERROR_ARB               to Type(ERROR, "ERROR"),
            ARBDebugOutput.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR_ARB  to Type(ERROR, "UNDEFINED BEHAVIOR"),
            ARBDebugOutput.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR_ARB to Type(WARN,  "DEPRECATED BEHAVIOR"),
            ARBDebugOutput.GL_DEBUG_TYPE_PORTABILITY_ARB         to Type(INFO,  "PORTABILITY"),
            ARBDebugOutput.GL_DEBUG_TYPE_PERFORMANCE_ARB         to Type(INFO,  "PERFORMANCE"),
            ARBDebugOutput.GL_DEBUG_TYPE_OTHER_ARB               to Type(DEBUG, "OTHER")
        )
    }

    private data class Type(val logLevel: LogLevel, val name: String)
}