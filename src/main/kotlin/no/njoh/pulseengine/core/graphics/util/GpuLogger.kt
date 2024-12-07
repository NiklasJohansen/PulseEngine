package no.njoh.pulseengine.core.graphics.util

import org.lwjgl.opengl.KHRDebug.*

/**
 * Utility class for logging GPU debug messages.
 * Gives more readable RenderDoc captures and is useful for debugging OpenGL errors.
 */
object GpuLogger
{
    private var ENABLED = false

    fun setEnable(state: Boolean) { ENABLED = state }

    fun beginGroup(label: CharSequence)
    {
        if (ENABLED) glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 0, label)
    }

    fun endGroup()
    {
        if (ENABLED) glPopDebugGroup()
    }
}