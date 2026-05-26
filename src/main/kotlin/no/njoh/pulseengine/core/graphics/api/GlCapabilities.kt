package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.glGetFloatv

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
     * True when multi-draw indirect entry points are available.
     *
     * Provided by OpenGL 4.3 or `GL_ARB_multi_draw_indirect`.
     */
    var multiDrawIndirect = false; private set

    /**
     * True when immutable persistent mapped buffer storage is available.
     *
     * Provided by OpenGL 4.4 or `GL_ARB_buffer_storage`.
     */
    var persistentMappedBuffers = false; private set

    /**
     * True when shader draw parameters are available through core OpenGL 4.6.
     */
    var shaderDrawParametersCore = false; private set

    /**
     * True when shader draw parameters are available through `GL_ARB_shader_draw_parameters`.
     */
    var shaderDrawParametersArb = false; private set

    /**
     * True when anisotropic texture filtering is available.
     *
     * Provided by OpenGL 4.6, `GL_ARB_texture_filter_anisotropic`, or `GL_EXT_texture_filter_anisotropic`.
     */
    var textureFilterAnisotropic = false; private set

    /**
     * Maximum anisotropy supported by the current OpenGL context.
     */
    var maxTextureAnisotropy = 1f; private set

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
        multiDrawIndirect = caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect
        persistentMappedBuffers = caps.OpenGL44 || caps.GL_ARB_buffer_storage
        textureFilterAnisotropic = caps.OpenGL46 || caps.GL_ARB_texture_filter_anisotropic || caps.GL_EXT_texture_filter_anisotropic
        maxTextureAnisotropy = 
            if (textureFilterAnisotropic) FloatArray(1).also { glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, it) }.first().coerceAtLeast(1f) 
            else 1f
    }
}