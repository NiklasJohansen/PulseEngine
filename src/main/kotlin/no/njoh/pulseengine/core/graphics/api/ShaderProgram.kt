package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.shared.primitives.Color
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.ARBUniformBufferObject.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL33.glVertexAttribDivisor

class ShaderProgram(val id: Int)
{
    // Used for getting matrix data as array
    private val floatArray16 = FloatArray(16)

    fun bind() = this.also { glUseProgram(id) }

    fun unbind() = this.also { glUseProgram(0) }

    fun delete()
    {
        unbind()
        glDeleteProgram(id)
    }

    fun getAttributeLocation(name: String) = glGetAttribLocation(id, name)

    fun getUniformLocation(name: String) = glGetUniformLocation(id, name)

    fun setUniform(name: String, vec3: Vector3f): Vector3f
    {
        glUniform3f(getUniformLocation(name), vec3[0], vec3[1], vec3[2])
        return vec3
    }

    fun setUniform(name: String, vec4: Vector4f): Vector4f
    {
        glUniform4f(getUniformLocation(name), vec4[0], vec4[1], vec4[2], vec4[3])
        return vec4
    }

    fun setUniform(name: String, matrix: Matrix4f): Matrix4f
    {
        glUniformMatrix4fv(getUniformLocation(name), false, matrix.get(floatArray16))
        return matrix
    }

    fun setUniform(name: String, value: Int): Int
    {
        glUniform1i(getUniformLocation(name), value)
        return value
    }

    fun setUniform(name: String, value: Boolean): Boolean
    {
        glUniform1i(getUniformLocation(name), if (value) 1 else 0)
        return value
    }

    fun setUniform(name: String, value: Float): Float
    {
        glUniform1f(getUniformLocation(name), value)
        return value
    }

    fun setUniform(name: String, value1: Float, value2: Float)
    {
        glUniform2f(getUniformLocation(name), value1, value2)
    }

    fun setUniform(name: String, value1: Float, value2: Float, value3: Float)
    {
        glUniform3f(getUniformLocation(name), value1, value2, value3)
    }

    fun setUniform(name: String, color: Color): Color
    {
        glUniform4f(getUniformLocation(name), color.red, color.green, color.blue, color.alpha)
        return color
    }

    fun defineVertexAttributeLayout(name: String, size: Int, type: Int, stride: Int, offset: Int, divisor: Int = 0, normalized: Boolean = false)
    {
        val location = getAttributeLocation(name)
        glEnableVertexAttribArray(location)
        glVertexAttribPointer(location, size, type, normalized, stride, offset.toLong())
        glVertexAttribDivisor(location, divisor)
    }

    fun defineVertexAttributeLayout(layout: VertexAttributeLayout)
    {
        var offset = 0L
        layout.attributes.forEach { attribute ->
            val location = getAttributeLocation(attribute.name)
            glEnableVertexAttribArray(location)
            glVertexAttribPointer(location, attribute.count, attribute.type, attribute.normalized, layout.strideInBytes.toInt(), offset)
            glVertexAttribDivisor(location, attribute.divisor)
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
        fun create(vertexShaderFileName: String, fragmentShaderFileName: String): ShaderProgram
        {
            val vertexShader = Shader.getOrLoad(vertexShaderFileName, ShaderType.VERTEX)
            val fragmentShader = Shader.getOrLoad(fragmentShaderFileName, ShaderType.FRAGMENT)
            return create(vertexShader, fragmentShader)
        }

        fun create(vararg shaders: Shader): ShaderProgram
        {
            val programId = glCreateProgram()
            for (shader in shaders)
                glAttachShader(programId, shader.id)

            glLinkProgram(programId)

            if (glGetProgrami(programId, GL_LINK_STATUS) != GL_TRUE)
                throw RuntimeException(glGetProgramInfoLog(programId))

            return ShaderProgram(programId)
        }
    }
}