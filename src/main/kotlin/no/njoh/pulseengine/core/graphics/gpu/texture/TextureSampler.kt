package no.njoh.pulseengine.core.graphics.gpu.texture

import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.shared.primitives.Color
import org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT
import org.lwjgl.opengl.GL33.*

data class TextureSampler(
    val id: Int,
    val filter: TextureFilter,
    val wrapping: TextureWrapping,
    val anisotropy: TextureAnisotropy,
    val compare: TextureCompare,
    val borderColor: Color?,
) {
    fun bind(textureUnit: Int) = glBindSampler(textureUnit, id)

    companion object
    {
        private val samplers = mutableListOf<TextureSampler>()

        fun getFor(
            filter: TextureFilter,
            anisotropy: TextureAnisotropy,
            wrapping: TextureWrapping,
            compare: TextureCompare,
            borderColor: Color?,
        ): TextureSampler {
            val sampler = samplers.find()
            {
                it.filter == filter &&
                it.anisotropy == anisotropy &&
                it.wrapping == wrapping &&
                it.compare == compare &&
                it.borderColor == borderColor
            }

            return sampler ?: create(filter, anisotropy, wrapping, compare, borderColor).also { samplers.add(it) }
        }

        private fun create(
            filter: TextureFilter,
            anisotropy: TextureAnisotropy,
            wrapping: TextureWrapping,
            compare: TextureCompare,
            borderColor: Color?,
        ): TextureSampler {
            
            val id = glGenSamplers()
            glSamplerParameteri(id, GL_TEXTURE_MIN_FILTER, filter.minValue)
            glSamplerParameteri(id, GL_TEXTURE_MAG_FILTER, filter.magValue)
            
            val anisotropyValue = resolveAnisotropyValue(anisotropy)
            if (anisotropyValue > 1f)
                glSamplerParameterf(id, GL_TEXTURE_MAX_ANISOTROPY_EXT, anisotropyValue)

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

            return TextureSampler(id, filter, wrapping, anisotropy, compare, borderColor)
        }

        fun resolveAnisotropyValue(anisotropy: TextureAnisotropy): Float =
            if (!GlCapabilities.textureFilterAnisotropic) TextureAnisotropy.OFF.value
            else if (anisotropy.value < 0f) GlCapabilities.maxTextureAnisotropy
            else anisotropy.value.coerceIn(1f, GlCapabilities.maxTextureAnisotropy)
    }
}