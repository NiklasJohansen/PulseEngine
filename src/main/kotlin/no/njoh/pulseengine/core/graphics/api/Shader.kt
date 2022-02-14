package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.graphics.api.ShaderType.FRAGMENT
import no.njoh.pulseengine.core.graphics.api.ShaderType.VERTEX
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL20.*
import java.io.FileNotFoundException

class Shader(
    id: Int,
    val type: ShaderType,
    val fileName: String
) {
    var id = id
        private set

    fun reload()
    {
        val newShader = load(fileName, type)
        if (loadedSuccessfully(newShader))
        {
            glDeleteShader(id)
            id = newShader.id
        }
    }

    fun delete()
    {
        if (loadedSuccessfully(this))
            glDeleteShader(id)
    }

    companion object
    {
        private val cache = mutableMapOf<String, Shader>()
        val errFragShader = load("/pulseengine/shaders/default/error.frag", FRAGMENT)
        val errVertShader = load("/pulseengine/shaders/default/error.vert", VERTEX)

        fun getOrLoad(fileName: String, type: ShaderType): Shader =
            cache.computeIfAbsent(fileName) { load(it, type) }

        fun reloadCache() =
            cache.values.forEach { it.reload() }

        fun clearCache()
        {
            cache.values.forEach { it.delete() }
            cache.clear()
        }

        private fun load(fileName: String, type: ShaderType) : Shader
        {
            try
            {
                val source = Shader::class.java.getResource(fileName)
                    ?: throw FileNotFoundException("could not locate file")

                val id = glCreateShader(type.value)
                glShaderSource(id, source.readText())
                glCompileShader(id)

                if (glGetShaderi(id, GL_COMPILE_STATUS) != GL_TRUE)
                    throw RuntimeException("\n" + glGetShaderInfoLog(id))

                Logger.debug("Loaded shader: $fileName")
                return Shader(id, type, fileName)
            }
            catch (e: Exception)
            {
                Logger.error("Failed to load shader: $fileName - Error message: ${e.message}")
                val errorShader = when (type)
                {
                    FRAGMENT -> errFragShader
                    VERTEX -> errVertShader
                }
                return Shader(errorShader.id, type, fileName)
            }
        }

        private fun loadedSuccessfully(shader: Shader) =
            shader.id != errFragShader.id && shader.id != errVertShader.id
    }
}
