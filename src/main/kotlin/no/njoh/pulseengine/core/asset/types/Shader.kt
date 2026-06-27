package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderType
import no.njoh.pulseengine.core.shared.utils.Extensions.loadTextFromPath

open class Shader(
    filePath: String,
    val type: ShaderType,
    val transform: (source: String) -> String = { it },
    name: String = filePath
) : Asset(filePath, name) {

    var currentId = INVALID_ID
        private set

    var sourceCode = ""
        private set

    var compileTimestamp = -1L
        private set

    private var errorId = INVALID_ID // The error ID is used when the loaded source code failed to compile

    override fun load()
    {
        sourceCode = filePath.loadTextFromPath() ?: throw Exception("Failed to load shader source code from file: $filePath")
    }

    override fun unload()
    {
        sourceCode = ""
    }

    fun getId() = if (currentId == INVALID_ID) errorId else currentId

    fun setId(id: Int)
    {
        currentId = id
        compileTimestamp = System.nanoTime()
    }

    fun setErrorId(id: Int)
    {
        errorId = id
    }

    companion object
    {
        const val INVALID_ID = -1
    }
}

class VertexShader(
    filePath: String,
    transform: (source: String) -> String = { it },
    name: String = filePath
) : Shader(filePath, ShaderType.VERTEX, transform, name)

class FragmentShader(
    filePath: String,
    transform: (source: String) -> String = { it },
    name: String = filePath
) : Shader(filePath, ShaderType.FRAGMENT, transform, name)

class ComputeShader(
    filePath: String,
    transform: (source: String) -> String = { it },
    name: String = filePath
) : Shader(filePath, ShaderType.COMPUTE, transform, name)
