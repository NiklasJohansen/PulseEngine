package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL20.*

class Shader(val id: Int, val type: ShaderType)
{
    fun delete() = glDeleteShader(id)

    companion object
    {
        private val cache = mutableMapOf<String, Shader>()

        fun getOrLoad(fileName: String, type: ShaderType): Shader =
            cache.computeIfAbsent(fileName) { load(it, type) }

        fun invalidateCache()
        {
            cache.values.forEach { it.delete() }
            cache.clear()
        }

        private fun load(fileName: String, type: ShaderType) : Shader
        {
            Logger.debug("Loading shader: $fileName")

            val source = Shader::class.java.getResource(fileName).readText()
            val id = glCreateShader(when (type)
            {
                ShaderType.FRAGMENT -> GL_FRAGMENT_SHADER
                ShaderType.VERTEX -> GL_VERTEX_SHADER
            })

            glShaderSource(id, source)
            glCompileShader(id)

            if (glGetShaderi(id, GL_COMPILE_STATUS) != GL_TRUE)
                throw RuntimeException(glGetShaderInfoLog(id))

            return Shader(id, type)
        }
    }
}
