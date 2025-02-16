package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL33.*

data class TextureSampler(
    val id: Int,
    val filter: TextureFilter
) {
    fun bind(textureUnit: Int) = glBindSampler(textureUnit, id)

    companion object
    {
        private val samplers = mutableMapOf<TextureFilter, TextureSampler>()

        fun getFor(filter: TextureFilter) = samplers.getOrPut(filter) { create(filter) }

        fun create(filter: TextureFilter): TextureSampler
        {
            val id = glGenSamplers()
            glSamplerParameteri(id, GL_TEXTURE_MIN_FILTER, filter.minValue)
            glSamplerParameteri(id, GL_TEXTURE_MAG_FILTER, filter.magValue)
            glSamplerParameteri(id, GL_TEXTURE_WRAP_S, GL_CLAMP)
            glSamplerParameteri(id, GL_TEXTURE_WRAP_T, GL_CLAMP)
            return TextureSampler(id, filter)
        }
    }
}