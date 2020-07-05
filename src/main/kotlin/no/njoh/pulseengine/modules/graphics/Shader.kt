package no.njoh.pulseengine.modules.graphics

import no.njoh.pulseengine.data.ShaderType
import org.lwjgl.opengl.GL20.*

class Shader(val id: Int, val type: ShaderType)
{
    fun delete() = glDeleteShader(id)

    companion object
    {
        fun load(fileName: String, type: ShaderType) : Shader
        {
            val source = Shader::class.java.getResource(fileName).readText()
            val id = glCreateShader(when(type)
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
