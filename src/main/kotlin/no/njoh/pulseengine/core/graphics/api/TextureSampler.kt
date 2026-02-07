package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.shared.primitives.Color
import org.lwjgl.opengl.GL33.*

data class TextureSampler(
    val id: Int,
    val filter: TextureFilter,
    val wrapping: TextureWrapping,
    val compare: TextureCompare,
    val borderColor: Color?
) {
    fun bind(textureUnit: Int) = glBindSampler(textureUnit, id)

    companion object
    {
        private val samplers = mutableListOf<TextureSampler>()

        fun getFor(filter: TextureFilter, wrapping: TextureWrapping, compare: TextureCompare, borderColor: Color?): TextureSampler
        {
            val sampler = samplers.find()
            {
                it.filter == filter &&
                it.wrapping == wrapping &&
                it.compare == compare &&
                it.borderColor == borderColor
            }

            return sampler ?: create(filter, wrapping, compare, borderColor).also { samplers.add(it) }
        }

        fun create(filter: TextureFilter, wrapping: TextureWrapping, compare: TextureCompare, borderColor: Color?): TextureSampler 
        {
            val id = glGenSamplers()
            glSamplerParameteri(id, GL_TEXTURE_MIN_FILTER, filter.minValue)
            glSamplerParameteri(id, GL_TEXTURE_MAG_FILTER, filter.magValue)
            
            glSamplerParameteri(id, GL_TEXTURE_WRAP_S, wrapping.value)
            glSamplerParameteri(id, GL_TEXTURE_WRAP_T, wrapping.value)

            glSamplerParameteri(id, GL_TEXTURE_COMPARE_MODE, compare.mode)
            if (compare.mode != GL_NONE)
                glSamplerParameteri(id, GL_TEXTURE_COMPARE_FUNC, compare.func)

            if (wrapping == TextureWrapping.CLAMP_TO_BORDER && borderColor != null)
            {
                val color = floatArrayOf(borderColor.red, borderColor.green, borderColor.blue, borderColor.alpha)
                glSamplerParameterfv(id, GL_TEXTURE_BORDER_COLOR, color)
            }

            return TextureSampler(id, filter, wrapping, compare, borderColor)
        }
    }
}