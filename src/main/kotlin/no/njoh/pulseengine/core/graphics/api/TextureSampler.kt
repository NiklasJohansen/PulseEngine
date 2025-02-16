package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL33.*

data class TextureSampler(
    val id: Int,
    val filter: TextureFilter,
    val wrapping: TextureWrapping
) {
    fun bind(textureUnit: Int) = glBindSampler(textureUnit, id)

    companion object
    {
        private val samplers = mutableListOf<TextureSampler>()

        fun getFor(filter: TextureFilter, wrapping: TextureWrapping): TextureSampler = samplers
            .find { it.filter == filter && it.wrapping == wrapping }
            ?: create(filter, wrapping).also { samplers.add(it) }

        fun create(filter: TextureFilter, wrapping: TextureWrapping): TextureSampler
        {
            val id = glGenSamplers()
            glSamplerParameteri(id, GL_TEXTURE_MIN_FILTER, filter.minValue)
            glSamplerParameteri(id, GL_TEXTURE_MAG_FILTER, filter.magValue)
            glSamplerParameteri(id, GL_TEXTURE_WRAP_S, wrapping.value)
            glSamplerParameteri(id, GL_TEXTURE_WRAP_T, wrapping.value)
            return TextureSampler(id, filter, wrapping)
        }
    }
}