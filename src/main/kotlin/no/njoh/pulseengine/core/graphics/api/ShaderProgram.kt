package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.graphics.api.ShaderType.FRAGMENT
import no.njoh.pulseengine.core.graphics.api.ShaderType.VERTEX
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.ARBUniformBufferObject.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL33.glVertexAttribDivisor

class ShaderProgram(
    id: Int,
    private val shaders: MutableList<Shader>
) {
    /** Locally mutable program ID */
    var id = id
        private set

    /** Cache of uniform locations */
    private var uniformLocations = mutableMapOf<String, Int>()

    /** Used for getting matrix data as an array */
    private val floatArray16 = FloatArray(16)

    fun bind() = this.also { glUseProgram(id) }

    fun unbind() = this.also { glUseProgram(0) }

    fun reload()
    {
        val newProgram = create(*shaders.toTypedArray())
        if (linkedSuccessfully(newProgram))
        {
            delete()
            id = newProgram.id
            shaders.clear()
            shaders.addAll(newProgram.shaders)
            shaderPrograms.add(this)
            uniformLocations.clear()
        }
    }

    fun delete()
    {
        if (linkedSuccessfully(this))
        {
            unbind()
            glDeleteProgram(id)
            shaderPrograms.remove(this)
        }
    }

    fun attributeLocationOf(name: String): Int =
        glGetAttribLocation(id, name)

    fun uniformLocationOf(name: String): Int =
        uniformLocations.getOrPut(name) { glGetUniformLocation(id, name) }

    fun setUniform(name: String, vec3: Vector3f) =
        glUniform3f(uniformLocationOf(name), vec3[0], vec3[1], vec3[2])

    fun setUniform(name: String, vec4: Vector4f) =
        glUniform4f(uniformLocationOf(name), vec4[0], vec4[1], vec4[2], vec4[3])

    fun setUniform(name: String, matrix: Matrix4f) =
        glUniformMatrix4fv(uniformLocationOf(name), false, matrix.get(floatArray16))

    fun setUniform(name: String, value: Int) =
        glUniform1i(uniformLocationOf(name), value)

    fun setUniform(name: String, value: Boolean) =
        glUniform1i(uniformLocationOf(name), if (value) 1 else 0)

    fun setUniform(name: String, value: Float) =
        glUniform1f(uniformLocationOf(name), value)

    fun setUniform(name: String, value1: Float, value2: Float) =
        glUniform2f(uniformLocationOf(name), value1, value2)

    fun setUniform(name: String, value1: Float, value2: Float, value3: Float) =
        glUniform3f(uniformLocationOf(name), value1, value2, value3)

    fun setUniform(name: String, color: Color) =
        glUniform4f(uniformLocationOf(name), color.red, color.green, color.blue, color.alpha)

    fun setVertexAttributeLayout(name: String, count: Int, type: Int, stride: Int, offset: Long, divisor: Int = 0, normalized: Boolean = false)
    {
        val location = attributeLocationOf(name)
        glEnableVertexAttribArray(location)
        glVertexAttribPointer(location, count, type, normalized, stride, offset)
        glVertexAttribDivisor(location, divisor)
    }

    fun setVertexAttributeLayout(layout: VertexAttributeLayout)
    {
        var offset = 0L
        layout.attributes.forEachFast { attribute ->
            setVertexAttributeLayout(
                name = attribute.name,
                count = attribute.count,
                type = attribute.type,
                stride = layout.strideInBytes.toInt(),
                offset = offset,
                divisor = attribute.divisor,
                normalized = attribute.normalized
            )
            offset += attribute.bytes
        }
    }

    fun assignUniformBlockBinding(blockName: String, blockBinding: Int): Int
    {
        val index = glGetUniformBlockIndex(id, blockName)
        glUniformBlockBinding(id, index, blockBinding)
        return index
    }

    companion object
    {
        private val shaderPrograms = mutableListOf<ShaderProgram>()
        private val errProgram = create(Shader.errVertShader, Shader.errFragShader)

        fun reloadAll()
        {
            shaderPrograms.toList().forEachFast { it.reload() }
        }

        fun create(vertexShaderFileName: String, fragmentShaderFileName: String): ShaderProgram
        {
            val vertexShader = Shader.getOrLoad(vertexShaderFileName, VERTEX)
            val fragmentShader = Shader.getOrLoad(fragmentShaderFileName, FRAGMENT)
            val program = create(vertexShader, fragmentShader)
            if (linkedSuccessfully(program))
                shaderPrograms.add(program)
            return program
        }

        private fun create(vararg shaders: Shader): ShaderProgram
        {
            val programId = glCreateProgram()
            for (shader in shaders)
                glAttachShader(programId, shader.id)

            glLinkProgram(programId)

            if (glGetProgrami(programId, GL_LINK_STATUS) != GL_TRUE)
            {
                Logger.error("Failed to link shaders: ${shaders.joinToString { it.fileName }} \n${glGetProgramInfoLog(programId)}")
                return errProgram
            }

            return ShaderProgram(programId, shaders.toMutableList())
        }

        private fun linkedSuccessfully(program: ShaderProgram) =
            program.id != errProgram.id
    }
}