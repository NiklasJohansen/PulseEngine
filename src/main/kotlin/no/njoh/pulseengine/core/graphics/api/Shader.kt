package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.graphics.api.ShaderType.FRAGMENT
import no.njoh.pulseengine.core.graphics.api.ShaderType.VERTEX
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.GL20.*
import java.io.File
import java.io.FileNotFoundException

class Shader(
    id: Int,
    fileName: String,
    val type: ShaderType,
) {
    var id = id
        private set

    var fileName = fileName
        private set

    fun reload(newFileName: String = fileName): Boolean
    {
        val newShader = load(newFileName, type)
        return if (loadedSuccessfully(newShader))
        {
            glDeleteShader(id)
            id = newShader.id
            fileName = newShader.fileName
            true
        }
        else false
    }

    fun delete()
    {
        if (loadedSuccessfully(this))
            glDeleteShader(id)
    }

    companion object
    {
        // Default error shaders
        val errFragShader = load("/pulseengine/shaders/default/error.frag", FRAGMENT)
        val errVertShader = load("/pulseengine/shaders/default/error.vert", VERTEX)

        // Cache of all loaded shaders
        private val cache = mutableMapOf<String, Shader>()

        fun getOrLoad(fileName: String, type: ShaderType): Shader =
            cache.getOrPut(fileName) { load(fileName, type) }

        fun getShaderFromAbsolutePath(path: String) =
            cache.firstNotNullOfOrNull { if (path.endsWith(it.key)) it.value else null }

        fun reloadAll() = cache.values.forEach { it.reload() }

        private fun load(fileName: String, type: ShaderType) : Shader
        {
            try
            {
                val file = File(fileName)
                val source = when
                {
                    file.isFile && file.isAbsolute -> file.readText()
                    else -> Shader::class.java.getResource(fileName)?.readText()
                        ?: throw FileNotFoundException("could not locate shader file")
                }

                val id = glCreateShader(type.value)
                glShaderSource(id, source)
                glCompileShader(id)

                if (glGetShaderi(id, GL_COMPILE_STATUS) != GL_TRUE)
                    throw RuntimeException("\n" + glGetShaderInfoLog(id))

                Logger.debug("Loaded shader: $fileName")
                return Shader(id, fileName, type)
            }
            catch (e: Exception)
            {
                Logger.error("Failed to load shader: $fileName - Error message: ${e.message}")
                val errorShader = when (type)
                {
                    FRAGMENT -> errFragShader
                    VERTEX -> errVertShader
                }
                return Shader(errorShader.id, fileName, type)
            }
        }

        private fun loadedSuccessfully(shader: Shader) =
            shader.id != errFragShader.id && shader.id != errVertShader.id
    }
}
