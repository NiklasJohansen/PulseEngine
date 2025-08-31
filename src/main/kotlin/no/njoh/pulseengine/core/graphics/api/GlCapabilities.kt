package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL

object GlCapabilities
{
    var baseInstance = false; private set

    fun create()
    {
        val caps = GL.createCapabilities()
        baseInstance = caps.OpenGL42 || caps.GL_ARB_base_instance
    }
}