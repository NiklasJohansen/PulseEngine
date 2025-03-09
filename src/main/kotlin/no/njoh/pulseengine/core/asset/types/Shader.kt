package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.graphics.api.ShaderType
import no.njoh.pulseengine.core.shared.utils.Extensions.loadTextFromDisk

open class Shader(
    filePath: String,
    val type: ShaderType,
    val transform: (source: String) -> String = { it }
) : Asset(filePath, filePath) {

    var currentId = INVALID_ID
        private set

    var sourceCode = ""
        private set

    var compileTimestamp = -1L
        private set

    private var errorId = INVALID_ID // Error id is used when the load source code failed to compile

    override fun load()
    {
        sourceCode = filePath.loadTextFromDisk() ?: throw Exception("Failed to load shader source code from file: $filePath")
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

    fun setErrorId(id: Int) { errorId = id }

    companion object
    {
        const val INVALID_ID = -1
    }
}

class VertexShader(filePath: String)   : Shader(filePath, ShaderType.VERTEX)
class FragmentShader(filePath: String) : Shader(filePath, ShaderType.FRAGMENT)
class ComputeShader(filePath: String)  : Shader(filePath, ShaderType.COMPUTE)