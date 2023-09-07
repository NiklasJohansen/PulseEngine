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
    val transform: (source: String) -> String
) {
    var id = id
        private set

    var fileName = fileName
        private set

    fun reload(newFileName: String = fileName): Boolean
    {
        val newShader = createShader(newFileName, type, transform)
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
        val errFragShader = createShader("/pulseengine/shaders/default/error.frag", FRAGMENT)
        val errVertShader = createShader("/pulseengine/shaders/default/error.vert", VERTEX)

        // Cache of all loaded shaders
        private val cache = mutableMapOf<String, Shader>()

        /**
         * Loads the shader from the given file.
         */
        fun load(fileName: String, type: ShaderType, transform: (source: String) -> String = { it }): Shader =
            createShader(fileName, type, transform).also { cache[fileName] = it }

        /**
         * Returns the shader from the cache if it exists, otherwise loads it from the given file.
         */
        fun getOrLoad(fileName: String, type: ShaderType, transform: (source: String) -> String = { it }): Shader =
            cache.getOrPut(fileName) { createShader(fileName, type, transform) }

        /**
         * Returns the shader with filename matching the given path, or null if it does not exist.
         */
        fun getShaderFromAbsolutePath(path: String) =
            cache.firstNotNullOfOrNull { if (path.endsWith(it.key)) it.value else null }

        /**
         * Reloads all cached shaders.
         */
        fun reloadAll() = cache.values.forEach { it.reload() }

        private fun createShader(fileName: String, type: ShaderType, transform: (source: String) -> String = { it }) : Shader
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
                glShaderSource(id, transform(source))
                glCompileShader(id)

                if (glGetShaderi(id, GL_COMPILE_STATUS) != GL_TRUE)
                    throw RuntimeException("\n" + glGetShaderInfoLog(id))

                Logger.debug("Loaded shader: $fileName")
                return Shader(id, fileName, type, transform)
            }
            catch (e: Exception)
            {
                Logger.error("Failed to load shader: $fileName - Error message: ${e.message}")
                val errorShader = when (type)
                {
                    FRAGMENT -> errFragShader
                    VERTEX -> errVertShader
                }
                return Shader(errorShader.id, fileName, type, transform = { it })
            }
        }

        private fun loadedSuccessfully(shader: Shader) =
            shader.id != errFragShader.id && shader.id != errVertShader.id
    }
}
