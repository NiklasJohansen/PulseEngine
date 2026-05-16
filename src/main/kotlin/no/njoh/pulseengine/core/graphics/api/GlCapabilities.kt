package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL

/**
 * Cached OpenGL feature flags for the current context.
 * These values are populated once after the OpenGL context is current.
 */
object GlCapabilities
{
    /**
     * True when `glDraw*BaseInstance` entry points are available.
     *
     * Provided by OpenGL 4.2 or `GL_ARB_base_instance`.
     */
    var baseInstance = false; private set

    /**
     * True when shaders can read draw parameters such as `gl_BaseInstance`.
     *
     * This is available either through core OpenGL 4.6 or `GL_ARB_shader_draw_parameters`.
     */
    var shaderDrawParameters = false; private set

    /**
     * True when shader draw parameters are available through core OpenGL 4.6.
     */
    var shaderDrawParametersCore = false; private set

    /**
     * True when shader draw parameters are available through `GL_ARB_shader_draw_parameters`.
     */
    var shaderDrawParametersArb = false; private set

    /**
     * Creates LWJGL's capability table for the current OpenGL context and caches selected feature flags.
     * Must be called after an OpenGL context has been made current.
     */
    fun create()
    {
        val caps = GL.createCapabilities()
        baseInstance = caps.OpenGL42 || caps.GL_ARB_base_instance
        shaderDrawParametersCore = caps.OpenGL46
        shaderDrawParametersArb = caps.GL_ARB_shader_draw_parameters
        shaderDrawParameters = shaderDrawParametersCore || shaderDrawParametersArb
    }
}